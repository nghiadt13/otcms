import { isApiError, type MsgCode } from "@/lib/api/apiError";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import { apiRequest } from "@/lib/api/client";
import type { CareRelationship } from "@/lib/domain/careRelationship";

export type DependentRelationship = Exclude<CareRelationship, "SELF">;

export interface AddDependentRequest {
  fullName: string;
  dateOfBirth: string;
  gender: string;
  relationship: DependentRelationship;
  nationalId?: string;
  identityDocumentType?: string;
}

export type DependentLinkResult =
  | { result: "LINKED"; linkId: string }
  | { result: "OTP_SENT"; linkId: string; maskedEmail: string }
  | { result: "COUNTER_REQUIRED"; linkId: string };

export function addDependent(input: AddDependentRequest) {
  return apiRequest<DependentLinkResult>("/api/v1/me/dependents", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function verifyDependentOtp(linkId: string, code: string) {
  return apiRequest<DependentLinkResult>(
    `/api/v1/me/dependents/links/${encodeURIComponent(linkId)}/otp/verify`,
    { method: "POST", body: JSON.stringify({ code }) },
  );
}

export function resendDependentOtp(linkId: string) {
  return apiRequest<DependentLinkResult>(
    `/api/v1/me/dependents/links/${encodeURIComponent(linkId)}/otp/resend`,
    { method: "POST" },
  );
}

export interface ManagedProfile {
  patientProfileId: string;
  fullName: string;
  dateOfBirth: string;
  gender: "MALE" | "FEMALE" | "OTHER";
  relationship: CareRelationship;
  linkStatus: "CONFIRMED" | "PENDING_OTP" | "PENDING_COUNTER";
  linkId: string | null;
}

export function listManagedProfiles(signal?: AbortSignal) {
  return apiRequest<{ items: ManagedProfile[] }>(
    "/api/v1/me/patient-profiles",
    {
      signal,
    },
  );
}

export function removeDependent(profileId: string) {
  return apiRequest<void>(
    `/api/v1/me/dependents/${encodeURIComponent(profileId)}`,
    { method: "DELETE" },
  );
}

const dependentErrors: Record<string, MsgCode> = {
  OTP_INVALID: "MSG-82",
  CARE_LINK_NOT_AVAILABLE: "MSG-21",
  VALIDATION_FAILED: "MSG-17",
};

export function dependentErrorMessage(error: unknown) {
  if (isApiError(error) && error.errorCode) {
    const code = dependentErrors[error.errorCode];
    if (code) return formatAppMessage(code);
  }
  return messageFromError(error);
}

export interface ProfileAdministrativeFields {
  fullName: string;
  dateOfBirth: string;
  gender: string;
  phone: string | null;
  address: string | null;
  nationalId: string | null;
  identityDocumentType: string | null;
}

export interface AdministrativeProfile extends Omit<
  ProfileAdministrativeFields,
  "phone"
> {
  patientProfileId: string;
  phoneCanonical: string | null;
  phoneOwnerType: string | null;
  version: number;
}

function profilePath(profileId: string, own: boolean) {
  return own
    ? ("/api/v1/me/patient-profile" as const)
    : (`/api/v1/me/dependents/${encodeURIComponent(profileId)}` as const);
}

export function getAdministrativeProfile(
  profileId: string,
  own: boolean,
  signal: AbortSignal,
) {
  return apiRequest<AdministrativeProfile>(profilePath(profileId, own), {
    signal,
  });
}

export function updateAdministrativeProfile(
  profile: AdministrativeProfile,
  own: boolean,
  fields: ProfileAdministrativeFields,
) {
  return apiRequest<AdministrativeProfile>(
    profilePath(profile.patientProfileId, own),
    {
      method: "PUT",
      body: JSON.stringify(
        own
          ? {
              ...fields,
              version: profile.version,
              phoneOwnerType: profile.phoneOwnerType,
            }
          : fields,
      ),
    },
  );
}
