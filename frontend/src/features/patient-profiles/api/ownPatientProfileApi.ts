import { ApiError } from "@/lib/api/apiError";
import { apiRequest } from "@/lib/api/client";
import { peekSession } from "@/lib/auth/authSession";
import type {
  OwnIdentityDocumentType,
  OwnPatientGender,
  OwnPatientProfile,
  OwnPatientProfileUpdate,
  OwnPhoneOwnerType,
} from "../types/ownPatientProfile";

const PATH = "/api/v1/me/patient-profile" as const;

function invalidResponse(): never {
  throw new ApiError(502, {
    code: "MSG-04",
    errorCode: "INVALID_PATIENT_PROFILE_RESPONSE",
  });
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === "object" && !Array.isArray(value);
}

function mapProfile(input: unknown): OwnPatientProfile {
  if (!isRecord(input)) return invalidResponse();
  const value = input;
  const phoneOwnerType = value.phoneOwnerType;
  const identityDocumentType = value.identityDocumentType;
  if (
    typeof value.patientProfileId !== "string" ||
    !value.patientProfileId ||
    typeof value.fullName !== "string" ||
    typeof value.phoneCanonical !== "string" ||
    !value.phoneCanonical ||
    (phoneOwnerType !== "PATIENT" && phoneOwnerType !== "GUARDIAN") ||
    typeof value.dateOfBirth !== "string" ||
    !/^\d{4}-\d{2}-\d{2}$/.test(value.dateOfBirth) ||
    (value.gender !== "MALE" &&
      value.gender !== "FEMALE" &&
      value.gender !== "OTHER") ||
    (value.address !== null && typeof value.address !== "string") ||
    (value.nationalId !== null && typeof value.nationalId !== "string") ||
    (identityDocumentType !== null &&
      identityDocumentType !== "CCCD" &&
      identityDocumentType !== "PERSONAL_ID" &&
      identityDocumentType !== "PASSPORT") ||
    typeof value.hasAccount !== "boolean" ||
    !Number.isSafeInteger(value.version) ||
    (value.version as number) < 0
  )
    return invalidResponse();

  return {
    patientProfileId: value.patientProfileId,
    fullName: value.fullName,
    phoneCanonical: value.phoneCanonical,
    phoneOwnerType: phoneOwnerType as OwnPhoneOwnerType,
    dateOfBirth: value.dateOfBirth,
    gender: value.gender as OwnPatientGender,
    sex: value.gender as OwnPatientGender,
    address: value.address,
    nationalId: value.nationalId,
    identityDocumentType:
      identityDocumentType as OwnIdentityDocumentType | null,
    hasAccount: value.hasAccount,
    version: value.version as number,
  };
}

function assertSameSession(accountIdAtStart: string | null): void {
  if (peekSession()?.accountId !== accountIdAtStart)
    throw new ApiError(401, { code: "MSG-14", errorCode: "SESSION_CHANGED" });
}

/** Fetches only the authenticated patient's own administrative profile. */
export async function getOwnPatientProfile(
  signal?: AbortSignal,
): Promise<OwnPatientProfile> {
  const accountIdAtStart = peekSession()?.accountId ?? null;
  const response: unknown = await apiRequest(PATH, { signal });
  assertSameSession(accountIdAtStart);
  return mapProfile(response);
}

/** Replaces the authenticated patient's profile using a complete, versioned snapshot. */
export async function updateOwnPatientProfile(
  body: OwnPatientProfileUpdate,
): Promise<OwnPatientProfile> {
  const accountIdAtStart = peekSession()?.accountId ?? null;
  const response: unknown = await apiRequest(PATH, {
    method: "PUT",
    body: JSON.stringify({
      fullName: body.fullName,
      phone: body.phoneCanonical,
      phoneOwnerType: body.phoneOwnerType,
      dateOfBirth: body.dateOfBirth,
      gender: body.gender,
      address: body.address,
      nationalId: body.nationalId,
      identityDocumentType: body.identityDocumentType,
      version: body.version,
    }),
  });
  assertSameSession(accountIdAtStart);
  return mapProfile(response);
}
