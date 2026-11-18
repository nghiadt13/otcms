import { apiRequest } from "@/lib/api/client";

export function unlinkPatientProfile(profileId: string): Promise<void> {
  return apiRequest<void>(
    `/api/v1/me/dependents/${encodeURIComponent(profileId)}`,
    { method: "DELETE" },
  );
}
