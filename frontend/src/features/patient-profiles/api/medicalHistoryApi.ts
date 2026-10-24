import { ApiError } from "@/lib/api/apiError";
import { mockFail } from "@/lib/api/mockServer";
import { invalidHistoryFields } from "../schema/medicalHistoryRules";
import type {
  MedicalHistory,
  MedicalHistoryUpdate,
} from "../types/patientProfile";
import {
  isLinkedToAccount,
  mockLatency,
  rejectFields,
} from "./patientProfileMockHelpers";
import { STALE_HISTORY_DETAIL } from "./patientProfileErrors";
import { emptyMedicalHistory, vietnamIsoNow } from "./medicalHistoryMockData";
import { MOCK_ACCOUNT_NAME } from "./patientProfileMockData";
import {
  findClinicRecord,
  findMedicalHistory,
  saveMedicalHistory,
} from "./patientProfileMockStore";

// LỚP API GIẢ tiền sử bệnh (chữ ký = REST thật). Chỉ đọc/ghi được tiền sử của hồ sơ đã gắn.

/** Hồ sơ không có → 404 MSG-41; có nhưng không gắn với tài khoản → 403 MSG-40. */
function assertAccessible(patientId: string): void {
  if (!findClinicRecord(patientId)) mockFail(404, "MSG-41");
  if (!isLinkedToAccount(patientId)) mockFail(403, "MSG-40");
}

/** GET /api/v1/patients/{id}/medical-history */
export async function getMedicalHistory(
  patientId: string,
  signal?: AbortSignal,
): Promise<MedicalHistory> {
  await mockLatency(signal);
  assertAccessible(patientId);
  return findMedicalHistory(patientId) ?? emptyMedicalHistory();
}

/**
 * PUT /api/v1/patients/{id}/medical-history. `version` phải bằng bản đang lưu, nếu không
 * trả 409 STALE (người khác đã sửa). Thành công: version tăng 1, ghi người và giờ sửa.
 */
export async function updateMedicalHistory(
  patientId: string,
  body: MedicalHistoryUpdate,
): Promise<MedicalHistory> {
  await mockLatency();
  assertAccessible(patientId);
  rejectFields(invalidHistoryFields(body));

  const current = findMedicalHistory(patientId) ?? emptyMedicalHistory();
  if (body.version !== current.version)
    throw new ApiError(409, { detail: STALE_HISTORY_DETAIL });

  const next: MedicalHistory = {
    conditions: body.conditions,
    allergies: body.allergies,
    procedures: body.procedures,
    injuries: body.injuries,
    orthopedicHistory: body.orthopedicHistory,
    updatedAt: vietnamIsoNow(),
    updatedBy: MOCK_ACCOUNT_NAME,
    version: current.version + 1,
  };
  saveMedicalHistory(patientId, next);
  return structuredClone(next);
}
