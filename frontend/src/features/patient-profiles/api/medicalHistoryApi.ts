import { apiRequest } from "@/lib/api/client";
import type {
  MedicalHistory,
  MedicalHistoryUpdate,
} from "../types/patientProfile";

/** GET KAN-28; quyền trên hồ sơ do backend kiểm tra ở mỗi request. */
export function getMedicalHistory(
  patientId: string,
  signal?: AbortSignal,
): Promise<MedicalHistory> {
  return apiRequest(
    `/api/v1/patient-profiles/${encodeURIComponent(patientId)}/medical-history`,
    { signal },
  );
}

/** PUT thay đủ năm mục và version đang xem, không gửi metadata do server sở hữu. */
export function updateMedicalHistory(
  patientId: string,
  body: MedicalHistoryUpdate,
): Promise<MedicalHistory> {
  return apiRequest(
    `/api/v1/patient-profiles/${encodeURIComponent(patientId)}/medical-history`,
    {
      method: "PUT",
      body: JSON.stringify({
        conditions: body.conditions,
        allergies: body.allergies,
        procedures: body.procedures,
        injuries: body.injuries,
        orthopedicHistory: body.orthopedicHistory,
        version: body.version,
      }),
    },
  );
}
