import type { CareRelationship } from "@/lib/domain/careRelationship";
import type { ApiFieldError } from "@/lib/api/apiError";
import { mockDelay, mockFail } from "@/lib/api/mockServer";
import { getLinkedProfileRecords } from "@/lib/patient-scope/linkedProfilesMockStore";
import type { LinkedProfile } from "@/lib/patient-scope/types";
import type { ProfileFieldErrors } from "../schema/patientProfileFieldRules";
import type { ClinicPatientRecord } from "./patientProfileMockData";

// Tiện ích của lớp API giả hồ sơ: độ trễ, lỗi 400 theo trường, tra cứu quyền gắn. Chỉ dùng
// trong thư mục api của feature.

export const mockLatency = (signal?: AbortSignal) =>
  mockDelay(process.env.NODE_ENV === "test" ? 0 : 300, signal);

/** Lỗi 400 MSG-17 kèm tên các trường sai. Danh sách rỗng thì không làm gì. */
export function rejectFields(fields: readonly string[]): void {
  if (fields.length === 0) return;
  const fieldErrors = fields.map(
    (field): ApiFieldError => ({ field, code: "MSG-17" }),
  );
  mockFail(400, "MSG-17", undefined, fieldErrors);
}

export function throwFieldErrors(errors: ProfileFieldErrors): void {
  rejectFields(Object.keys(errors));
}

export function isLinkedToAccount(profileId: string): boolean {
  return getLinkedProfileRecords().some((item) => item.id === profileId);
}

/** Dòng đã gắn của hồ sơ, hoặc 404 MSG-41 khi tài khoản không gắn hồ sơ này. */
export function linkedEntryOrFail(profileId: string): LinkedProfile {
  return (
    getLinkedProfileRecords().find((item) => item.id === profileId) ??
    mockFail(404, "MSG-41")
  );
}

export function toLinkedProfile(
  record: ClinicPatientRecord,
  relationship: CareRelationship,
): LinkedProfile {
  return {
    id: record.id,
    patientCode: record.patientCode,
    fullName: record.fullName,
    dateOfBirth: record.dateOfBirth,
    sex: record.sex,
    relationship,
  };
}
