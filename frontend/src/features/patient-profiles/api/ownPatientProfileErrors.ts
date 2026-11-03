import { ApiError, isApiError, toFieldErrorMap } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import type { ProfileFieldErrors } from "../schema/patientProfileFieldRules";

export function isOwnProfileStale(error: unknown): boolean {
  return (
    isApiError(error) &&
    error.status === 409 &&
    error.errorCode === "STALE_VERSION"
  );
}

export function isOwnProfileMissing(error: unknown): boolean {
  return isApiError(error) && error.status === 404;
}

export function isOwnProfileAccessLost(error: unknown): boolean {
  return (
    (isApiError(error) && error.status === 401) ||
    (error instanceof Error &&
      error.name === "AuthSessionError" &&
      "status" in error &&
      (Number(error.status) === 401 || Number(error.status) === 403)) ||
    (error instanceof Error &&
      error.message === "Bạn cần đăng nhập để tiếp tục.")
  );
}

export function ownProfileErrorMessage(error: unknown): string {
  if (isOwnProfileAccessLost(error)) return formatAppMessage("MSG-14");
  if (isApiError(error)) {
    if (error.status === 401) return formatAppMessage("MSG-14");
    if (error.status === 403 || error.status === 404)
      return formatAppMessage("MSG-19");
    if (error.status === 400) return formatAppMessage("MSG-08");
  }
  return formatAppMessage(isOwnProfileStale(error) ? "MSG-16" : "MSG-04");
}

export function ownProfileFieldErrors(
  error: unknown,
): ProfileFieldErrors | undefined {
  if (!isApiError(error) || error.status !== 400) return undefined;
  const mapped: ProfileFieldErrors = {};
  const aliases: Record<string, keyof ProfileFieldErrors> = {
    fullName: "fullName",
    dateOfBirth: "dateOfBirth",
    gender: "sex",
    nationalId: "citizenId",
    identityDocumentType: "citizenId",
    phone: "phone",
    phoneCanonical: "phone",
    phoneOwnerType: "phone",
    address: "address",
  };
  const fieldErrors = toFieldErrorMap(error.fieldErrors);
  for (const [field, code] of Object.entries(fieldErrors)) {
    const target = aliases[field];
    if (target) mapped[target] = formatAppMessage(code);
  }
  return Object.keys(mapped).length ? mapped : undefined;
}

export function asOwnProfileStaleError(): ApiError {
  return new ApiError(409, { code: "MSG-16", errorCode: "STALE_VERSION" });
}

export function ownProfileDisplayError(error: unknown): ApiError {
  if (isApiError(error)) {
    if (error.status === 401) return new ApiError(401, { code: "MSG-14" });
    if (error.status === 403 || error.status === 404)
      return new ApiError(error.status, { code: "MSG-19" });
    if (error.status === 400) return new ApiError(400, { code: "MSG-08" });
    if (isOwnProfileStale(error)) return asOwnProfileStaleError();
    if (error.code) return error;
  }
  if (isOwnProfileAccessLost(error))
    return new ApiError(401, { code: "MSG-14" });
  return new ApiError(500, { code: "MSG-04" });
}
