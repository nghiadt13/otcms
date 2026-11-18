import { ApiError } from "@/lib/api/apiError";
import { apiRequest } from "@/lib/api/client";

interface LinkResultResponse {
  maskedEmail?: string;
  profile?: { patientProfileId: string };
}

/** Verify the code sent to the owner of an existing profile. */
export async function verifyDependentOtp(
  linkId: string,
  code: string,
): Promise<{ profileId: string }> {
  const response = await apiRequest<LinkResultResponse>(
    `/api/v1/me/dependents/links/${encodeURIComponent(linkId)}/otp/verify`,
    { method: "POST", body: JSON.stringify({ code }) },
  );
  if (!response.profile) throw new ApiError(500, { code: "MSG-04" });
  return { profileId: response.profile.patientProfileId };
}

/** Resend the code to the existing profile owner. */
export async function resendDependentOtp(
  linkId: string,
): Promise<{ maskedEmail?: string }> {
  const response = await apiRequest<LinkResultResponse>(
    `/api/v1/me/dependents/links/${encodeURIComponent(linkId)}/otp/resend`,
    { method: "POST" },
  );
  return { maskedEmail: response.maskedEmail };
}
