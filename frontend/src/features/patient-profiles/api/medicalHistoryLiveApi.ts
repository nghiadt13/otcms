import { apiRequest } from "@/lib/api/client";
import type {
  MedicalHistory,
  MedicalHistoryUpdate,
} from "../types/patientProfile";

function historyPath(patientId: string): `/api/v1/${string}` {
  return `/api/v1/patient-profiles/${encodeURIComponent(patientId)}/medical-history`;
}

export function getLiveMedicalHistory(
  patientId: string,
  signal?: AbortSignal,
): Promise<MedicalHistory> {
  return apiRequest<MedicalHistory>(historyPath(patientId), { signal });
}

export function updateLiveMedicalHistory(
  patientId: string,
  body: MedicalHistoryUpdate,
): Promise<MedicalHistory> {
  return apiRequest<MedicalHistory>(historyPath(patientId), {
    method: "PUT",
    body: JSON.stringify(body),
  });
}
