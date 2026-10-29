import { apiRequest } from "@/lib/api/client";

export interface CurrentPatientAccount {
  id: string;
  /** Null for legacy accounts where registration name was never persisted. */
  displayName: string | null;
  email: string;
  status: string;
}

/** Authenticated self-service identity. The backend derives the account from the access token. */
export function getCurrentPatientAccount(
  signal?: AbortSignal,
): Promise<CurrentPatientAccount> {
  return apiRequest<CurrentPatientAccount>("/api/v1/me/patient-account", { signal });
}

/** Save the display name for accounts created before registration names were persisted. */
export function updateCurrentPatientDisplayName(
  displayName: string,
): Promise<CurrentPatientAccount> {
  return apiRequest<CurrentPatientAccount>("/api/v1/me", {
    method: "PATCH",
    body: JSON.stringify({ displayName }),
  });
}
