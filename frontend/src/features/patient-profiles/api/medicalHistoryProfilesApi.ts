import { apiRequest } from "@/lib/api/client";
import type { CareRelationship } from "@/lib/domain/careRelationship";

/** Projection KAN-26: không dùng id/allowedActions giả của hồ sơ hành chính. */
export interface MedicalHistoryProfile {
  patientProfileId: string;
  fullName: string;
  relationship: CareRelationship;
  linkStatus: string;
}

export function listMedicalHistoryProfiles(
  signal?: AbortSignal,
): Promise<{ items: MedicalHistoryProfile[] }> {
  return apiRequest("/api/v1/me/patient-profiles", { signal });
}

/** Chặn trạng thái chưa xác nhận, kể cả response có SELF nhưng linkStatus không hợp lệ. */
export function canOpenMedicalHistory(profile: MedicalHistoryProfile): boolean {
  return profile.linkStatus === "CONFIRMED";
}
