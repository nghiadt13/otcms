import { ApiError } from "@/lib/api/apiError";
import { apiRequest } from "@/lib/api/client";
import { mockDelay } from "@/lib/api/mockServer";
import type {
  AddDependentResult,
  CreateProfileRequest,
} from "../types/patientProfile";
import {
  createPatientProfile,
  toAddDependentBody,
  toAddDependentResult,
  type AddDependentResponse,
} from "./patientProfileApi";

// Bước xác thực OTP của UC13 (AF1). BE gửi mã tới email lưu trên hồ sơ trùng, không phải
// email người dùng nhập (BR-17); FE chỉ nhận lại `maskedEmail` để hiển thị.

/** LinkResult của BE; `profile` chỉ có khi result = LINKED. */
interface LinkResultResponse {
  result: string;
  maskedEmail?: string;
  profile?: { patientProfileId: string };
}

/** Mã OTP của kho giả (chỉ dùng trong test). */
export const MOCK_DEPENDENT_OTP = "123456";

/** POST /api/v1/me/dependents/links/{linkId}/otp/verify. Sai/hết hạn → 400 MSG-82. */
export async function verifyDependentOtp(
  linkId: string,
  code: string,
): Promise<{ profileId: string }> {
  if (process.env.NODE_ENV !== "test") {
    const response = await apiRequest<LinkResultResponse>(
      `/api/v1/me/dependents/links/${encodeURIComponent(linkId)}/otp/verify`,
      { method: "POST", body: JSON.stringify({ code }) },
    );
    if (!response.profile) throw new ApiError(500, { code: "MSG-04" });
    return { profileId: response.profile.patientProfileId };
  }
  await mockDelay(0);
  if (code !== MOCK_DEPENDENT_OTP) throw new ApiError(400, { code: "MSG-82" });
  return { profileId: linkId };
}

/** POST /api/v1/me/dependents/links/{linkId}/otp/resend → 202 với email đã che. */
export async function resendDependentOtp(
  linkId: string,
): Promise<{ maskedEmail?: string }> {
  if (process.env.NODE_ENV !== "test") {
    const response = await apiRequest<LinkResultResponse>(
      `/api/v1/me/dependents/links/${encodeURIComponent(linkId)}/otp/resend`,
      { method: "POST" },
    );
    return { maskedEmail: response.maskedEmail };
  }
  await mockDelay(0);
  return { maskedEmail: "b***@example.com" };
}

/** Kết quả yêu cầu gửi OTP thêm người thân: mã yêu cầu cần xác thực và email đã che. */
export interface AddOtpRequest {
  requestId: string;
  maskedEmail?: string;
}

/** Kho giả: thân form của từng yêu cầu đang chờ OTP (chỉ dùng trong test). */
const mockPendingAdds = new Map<string, CreateProfileRequest>();

/**
 * POST /api/v1/me/dependents/add-requests: BE kiểm tra thông tin rồi gửi OTP tới email của
 * chính tài khoản đang đăng nhập; chưa tạo hồ sơ nào cho tới khi xác thực mã.
 */
export async function requestAddDependentOtp(
  body: CreateProfileRequest,
): Promise<AddOtpRequest> {
  if (process.env.NODE_ENV !== "test")
    return apiRequest<AddOtpRequest>("/api/v1/me/dependents/add-requests", {
      method: "POST",
      body: JSON.stringify(toAddDependentBody(body)),
    });
  await mockDelay(0);
  const requestId = `mock-add-request-${mockPendingAdds.size + 1}`;
  mockPendingAdds.set(requestId, body);
  return { requestId, maskedEmail: "***@example.com" };
}

/** POST .../add-requests/{id}/verify: đúng mã thì BE thêm người thân (LINKED / OTP chủ hồ sơ / ra quầy). */
export async function verifyAddDependentOtp(
  requestId: string,
  code: string,
): Promise<AddDependentResult> {
  if (process.env.NODE_ENV !== "test") {
    const response = await apiRequest<AddDependentResponse>(
      `/api/v1/me/dependents/add-requests/${encodeURIComponent(requestId)}/verify`,
      { method: "POST", body: JSON.stringify({ code }) },
    );
    return toAddDependentResult(response);
  }
  await mockDelay(0);
  const body = mockPendingAdds.get(requestId);
  if (!body || code !== MOCK_DEPENDENT_OTP)
    throw new ApiError(400, { code: "MSG-82" });
  mockPendingAdds.delete(requestId);
  return createPatientProfile(body);
}

/** POST .../add-requests/{id}/resend: gửi lại mã; BE trả mã yêu cầu mới. */
export async function resendAddDependentOtp(
  requestId: string,
): Promise<AddOtpRequest> {
  if (process.env.NODE_ENV !== "test")
    return apiRequest<AddOtpRequest>(
      `/api/v1/me/dependents/add-requests/${encodeURIComponent(requestId)}/resend`,
      { method: "POST" },
    );
  await mockDelay(0);
  const body = mockPendingAdds.get(requestId);
  if (!body) throw new ApiError(404, { code: "MSG-21" });
  mockPendingAdds.delete(requestId);
  const next = `${requestId}-r`;
  mockPendingAdds.set(next, body);
  return { requestId: next, maskedEmail: "***@example.com" };
}
