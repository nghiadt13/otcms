import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";

const { apiRequest, session } = vi.hoisted(() => ({
  apiRequest: vi.fn(),
  session: { accountId: "patient-a" as string | null },
}));

vi.mock("@/lib/api/client", () => ({ apiRequest }));
vi.mock("@/lib/auth/authSession", async (importOriginal) => ({
  ...(await importOriginal()),
  peekSession: () => session,
}));

import {
  getOwnPatientProfile,
  updateOwnPatientProfile,
} from "./ownPatientProfileApi";

const response = {
  patientProfileId: "profile-1",
  fullName: "Nguyễn An",
  phoneCanonical: "+84912345678",
  phoneOwnerType: "PATIENT",
  dateOfBirth: "1990-05-20",
  gender: "MALE",
  address: "Hà Nội",
  nationalId: null,
  identityDocumentType: null,
  hasAccount: true,
  version: 7,
};

beforeEach(() => {
  apiRequest.mockReset();
  session.accountId = "patient-a";
});

describe("ownPatientProfileApi", () => {
  it("GET dùng endpoint self, AbortSignal và chỉ map field từ response", async () => {
    apiRequest.mockResolvedValue(response);
    const controller = new AbortController();
    const detail = await getOwnPatientProfile(controller.signal);
    expect(apiRequest).toHaveBeenCalledWith("/api/v1/me/patient-profile", {
      signal: controller.signal,
    });
    expect(detail).toEqual({ ...response, sex: "MALE" });
    expect("patientCode" in detail).toBe(false);
    expect("editableFields" in detail).toBe(false);
  });

  it("PUT gửi đủ DTO, đúng version và dùng response đã chuẩn hóa", async () => {
    apiRequest.mockResolvedValue({
      ...response,
      fullName: "Nguyễn An Chuẩn hóa",
      version: 8,
    });
    const updated = await updateOwnPatientProfile({
      fullName: " Nguyễn An ",
      phoneCanonical: "+84912345678",
      phoneOwnerType: "PATIENT",
      dateOfBirth: "1990-05-20",
      gender: "MALE",
      address: "Hà Nội",
      nationalId: null,
      identityDocumentType: null,
      version: 7,
    });
    expect(apiRequest).toHaveBeenCalledWith("/api/v1/me/patient-profile", {
      method: "PUT",
      body: JSON.stringify({
        fullName: " Nguyễn An ",
        phone: "+84912345678",
        phoneOwnerType: "PATIENT",
        dateOfBirth: "1990-05-20",
        gender: "MALE",
        address: "Hà Nội",
        nationalId: null,
        identityDocumentType: null,
        version: 7,
      }),
    });
    expect(updated.fullName).toBe("Nguyễn An Chuẩn hóa");
    expect(updated.version).toBe(8);
  });

  it("không áp dụng response của phiên A sau khi session đã chuyển sang B", async () => {
    apiRequest.mockImplementation(async () => {
      session.accountId = "patient-b";
      return response;
    });
    await expect(getOwnPatientProfile()).rejects.toMatchObject({
      status: 401,
      errorCode: "SESSION_CHANGED",
    });
  });

  it("truyền nguyên lỗi API validation, stale và quyền để lớp form xử lý theo mã", async () => {
    const stale = new ApiError(409, { errorCode: "STALE_VERSION" });
    apiRequest.mockRejectedValue(stale);
    await expect(
      updateOwnPatientProfile({
        fullName: "Nguyễn An",
        phoneCanonical: "+84912345678",
        phoneOwnerType: "PATIENT",
        dateOfBirth: "1990-05-20",
        gender: "MALE",
        address: "Hà Nội",
        nationalId: null,
        identityDocumentType: null,
        version: 7,
      }),
    ).rejects.toBe(stale);
  });
});
