import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import { getMedicalHistory, updateMedicalHistory } from "./medicalHistoryApi";
import { isStaleHistoryError } from "./patientProfileErrors";
import {
  MedicalHistoryTestServer,
  HISTORY_PATH,
  HISTORY_PROFILE_ID,
  emptyHistory,
} from "./medicalHistoryTestServer";
import type { MedicalHistoryUpdate } from "../types/patientProfile";

vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
let server: MedicalHistoryTestServer;

beforeEach(() => {
  vi.resetAllMocks();
  server = new MedicalHistoryTestServer();
  vi.mocked(apiRequest).mockImplementation(server.request);
});

function body(version: number): MedicalHistoryUpdate {
  return {
    conditions: "Bệnh nền",
    allergies: "Dị ứng",
    procedures: "Thủ thuật",
    injuries: "Chấn thương",
    orthopedicHistory: "Chỉnh hình",
    version,
  };
}

describe("medicalHistoryApi — KAN-28", () => {
  it("GET gọi endpoint thật và truyền AbortSignal; không cần profile mock", async () => {
    const controller = new AbortController();
    const result = await getMedicalHistory(
      HISTORY_PROFILE_ID,
      controller.signal,
    );
    expect(apiRequest).toHaveBeenCalledWith(HISTORY_PATH, {
      signal: controller.signal,
    });
    expect(result).toEqual(server.history);
  });

  it("encode ID trong URL", async () => {
    await getMedicalHistory("id/with?characters");
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/v1/patient-profiles/id%2Fwith%3Fcharacters/medical-history",
      { signal: undefined },
    );
  });

  it("PUT gửi đúng đủ năm trường và version, không gửi metadata", async () => {
    const request = {
      ...body(3),
      updatedAt: "client-controlled",
      updatedBy: "client-controlled",
    };
    const saved = await updateMedicalHistory(HISTORY_PROFILE_ID, request);
    expect(apiRequest).toHaveBeenCalledWith(HISTORY_PATH, {
      method: "PUT",
      body: JSON.stringify(body(3)),
    });
    expect(saved).toEqual(server.history);
    expect(saved).toMatchObject({ version: 4, updatedBy: "Người cập nhật" });
    expect(await getMedicalHistory(HISTORY_PROFILE_ID)).toEqual(saved);
  });

  it("chưa có tiền sử: version 0 -> PUT version 1 -> GET đọc lại dữ liệu đã lưu", async () => {
    server.history = emptyHistory();
    expect((await getMedicalHistory(HISTORY_PROFILE_ID)).version).toBe(0);
    const saved = await updateMedicalHistory(HISTORY_PROFILE_ID, body(0));
    expect(saved.version).toBe(1);
    expect(await getMedicalHistory(HISTORY_PROFILE_ID)).toEqual(saved);
  });

  it("hai bản cùng version: bản sau nhận STALE_VERSION và không ghi đè", async () => {
    const first = await getMedicalHistory(HISTORY_PROFILE_ID);
    const second = await getMedicalHistory(HISTORY_PROFILE_ID);
    await updateMedicalHistory(HISTORY_PROFILE_ID, body(first.version));
    await expect(
      updateMedicalHistory(HISTORY_PROFILE_ID, {
        ...body(second.version),
        allergies: "Ghi đè",
      }),
    ).rejects.toMatchObject({ status: 409, errorCode: "STALE_VERSION" });
    expect((await getMedicalHistory(HISTORY_PROFILE_ID)).allergies).toBe(
      "Dị ứng",
    );
  });

  it.each([
    [400, "VALIDATION_FAILED"],
    [403, "ACCESS_DENIED"],
    [404, "PATIENT_PROFILE_NOT_FOUND"],
  ])(
    "giữ ApiError HTTP %s và errorCode tại ranh giới API",
    async (status, errorCode) => {
      const error = new ApiError(status, { errorCode });
      server.getError = error;
      server.putError = error;
      await expect(getMedicalHistory(HISTORY_PROFILE_ID)).rejects.toBe(error);
      await expect(
        updateMedicalHistory(HISTORY_PROFILE_ID, body(3)),
      ).rejects.toBe(error);
    },
  );

  it("chỉ errorCode STALE_VERSION là stale; không dựa vào error.message", () => {
    expect(
      isStaleHistoryError(
        new ApiError(409, {
          errorCode: "STALE_VERSION",
          detail: "Không phải STALE",
        }),
      ),
    ).toBe(true);
    expect(isStaleHistoryError(new ApiError(409, { detail: "STALE" }))).toBe(
      false,
    );
  });
});
