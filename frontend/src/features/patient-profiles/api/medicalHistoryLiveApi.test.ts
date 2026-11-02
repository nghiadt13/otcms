import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import { isStaleHistoryError } from "./patientProfileErrors";
import {
  getLiveMedicalHistory,
  updateLiveMedicalHistory,
} from "./medicalHistoryLiveApi";
import type {
  MedicalHistory,
  MedicalHistoryUpdate,
} from "../types/patientProfile";

vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));

const history: MedicalHistory = {
  conditions: "",
  allergies: "",
  procedures: "",
  injuries: "",
  orthopedicHistory: "",
  updatedAt: null,
  updatedBy: null,
  version: 0,
};

beforeEach(() => vi.mocked(apiRequest).mockReset());

describe("medical history live API", () => {
  it("recognizes the R3 stale-version response", () => {
    expect(isStaleHistoryError(new ApiError(409, { errorCode: "STALE_VERSION" }))).toBe(true);
  });
  it("đọc đúng hồ sơ qua GET có thể hủy", async () => {
    const controller = new AbortController();
    vi.mocked(apiRequest).mockResolvedValueOnce(history);
    expect(await getLiveMedicalHistory("patient-1", controller.signal)).toEqual(
      history,
    );
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/v1/patient-profiles/patient-1/medical-history",
      { signal: controller.signal },
    );
  });

  it("lưu đủ năm mục và version bằng PUT, trả bản mới từ server", async () => {
    const update: MedicalHistoryUpdate = {
      conditions: "Hen suyễn",
      allergies: "",
      procedures: "",
      injuries: "",
      orthopedicHistory: "",
      version: 3,
    };
    const saved = {
      ...update,
      version: 4,
      updatedAt: "2026-11-02T10:00:00+07:00",
      updatedBy: "Bác sĩ A",
    };
    vi.mocked(apiRequest).mockResolvedValueOnce(saved);
    expect(await updateLiveMedicalHistory("patient-1", update)).toEqual(saved);
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/v1/patient-profiles/patient-1/medical-history",
      {
        method: "PUT",
        body: JSON.stringify(update),
      },
    );
  });
});
