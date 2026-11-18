import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, isApiError } from "@/lib/api/apiError";
import { listLinkedProfiles } from "@/lib/patient-scope/linkedProfilesApi";
import { getLinkedProfileRecords } from "@/lib/patient-scope/linkedProfilesMockStore";
import type { CreateProfileRequest } from "../types/patientProfile";
import {
  createPatientProfile,
  getPatientProfile,
  updatePatientProfile,
} from "./patientProfileApi";
import { apiRequest } from "@/lib/api/client";

vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
import { PROFILE_MOCK_IDS } from "./patientProfileMockData";
import { resetPatientProfileMock } from "./patientProfileMockStore";

const { an, lan, huy, binh, bao } = PROFILE_MOCK_IDS;

const NEW_PROFILE: CreateProfileRequest = {
  relationship: "CHILD",
  fullName: "Trần Minh Khang",
  dateOfBirth: "2019-04-03",
  sex: "MALE",
};

/** Lỗi của một lời gọi API giả; test fail nếu lời gọi không ném ApiError. */
async function failureOf(promise: Promise<unknown>): Promise<ApiError> {
  const reason: unknown = await promise.then(
    () => undefined,
    (error: unknown) => error,
  );
  if (!isApiError(reason)) throw new Error("Kỳ vọng ném ApiError");
  return reason;
}

beforeEach(() => {
  resetPatientProfileMock();
});

describe("createPatientProfile", () => {
  it("gửi thông tin tới API và nhận hồ sơ mới đã liên kết", async () => {
    vi.mocked(apiRequest).mockResolvedValueOnce({ result: "LINKED", profile: { patientProfileId: "new-id" } });
    await expect(createPatientProfile(NEW_PROFILE)).resolves.toEqual({ status: "LINKED", profileId: "new-id" });
    expect(apiRequest).toHaveBeenCalledWith("/api/v1/me/dependents", expect.objectContaining({ method: "POST" }));
  });

  it("trả lỗi xác thực từ API", async () => {
    vi.mocked(apiRequest).mockRejectedValueOnce(new ApiError(400, { code: "MSG-17" }));
    expect(await failureOf(createPatientProfile(NEW_PROFILE))).toMatchObject({ status: 400, code: "MSG-17" });
  });
});

describe("getPatientProfile và updatePatientProfile", () => {
  it("hồ sơ đã có lượt khám: họ tên và ngày sinh không nằm trong editableFields", async () => {
    const detail = await getPatientProfile(lan);
    expect(detail.hasVisits).toBe(true);
    expect(detail.editableFields).not.toContain("fullName");
    expect(detail.editableFields).not.toContain("dateOfBirth");
    expect(detail.editableFields).toContain("phone");
  });

  it("sửa họ tên của hồ sơ đã khám → 400 MSG-17 tại trường fullName", async () => {
    const error = await failureOf(
      updatePatientProfile(lan, { fullName: "Nguyễn Thị Lan 2" }),
    );
    expect(error).toMatchObject({ status: 400, code: "MSG-17" });
    expect(error.fieldErrors).toEqual([{ field: "fullName", code: "MSG-17" }]);
  });

  it("sửa hợp lệ hồ sơ chưa khám: danh sách đã gắn đổi theo ngay", async () => {
    const updated = await updatePatientProfile(huy, {
      phone: "0911222333",
      fullName: "Nguyễn Gia Huy Anh",
    });
    expect(updated).toMatchObject({
      phone: "0911222333",
      fullName: "Nguyễn Gia Huy Anh",
    });
    const { items } = await listLinkedProfiles();
    expect(items.find((item) => item.id === huy)?.fullName).toBe(
      "Nguyễn Gia Huy Anh",
    );
  });

  it("hồ sơ không gắn với tài khoản → 404 MSG-41", async () => {
    expect(await failureOf(getPatientProfile(binh))).toMatchObject({
      status: 404,
      code: "MSG-41",
    });
    expect(
      await failureOf(updatePatientProfile(bao, { phone: "0911000000" })),
    ).toMatchObject({ status: 404, code: "MSG-41" });
  });
});

describe("dữ liệu mặc định khớp kho gắn dùng chung", () => {
  it("ba hồ sơ đã gắn mặc định có cùng id, mã và tên ở cả hai kho", async () => {
    const linked = getLinkedProfileRecords();
    for (const id of [an, lan, huy]) {
      const entry = linked.find((item) => item.id === id);
      const detail = await getPatientProfile(id);
      expect(entry).toMatchObject({
        patientCode: detail.patientCode,
        fullName: detail.fullName,
        dateOfBirth: detail.dateOfBirth,
        relationship: detail.relationship,
      });
    }
  });
});
