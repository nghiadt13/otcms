import { getAdministrativeProfile } from "./dependentApi";
import { ApiError } from "@/lib/api/apiError";
import { apiRequest } from "@/lib/api/client";
import { mockFail } from "@/lib/api/mockServer";
import type { CareRelationship } from "@/lib/domain/careRelationship";
import {
  getLinkedProfileRecords,
  replaceLinkedProfilesMock,
} from "@/lib/patient-scope/linkedProfilesMockStore";
import {
  CREATE_PROFILE_FIELDS,
  EDITABLE_FIELDS_AFTER_VISIT,
  EDITABLE_FIELDS_BEFORE_VISIT,
  validateProfileFields,
} from "../schema/patientProfileFieldRules";
import { normalizePhone } from "../schema/patientProfileFieldChecks";
import { valuesFromRequest } from "../schema/patientProfileRequestMapping";
import type {
  CreateProfileRequest,
  PatientProfileDetail,
  UpdateProfileRequest,
} from "../types/patientProfile";
import {
  linkedEntryOrFail,
  mockLatency,
  rejectFields,
  throwFieldErrors,
  toLinkedProfile,
} from "./patientProfileMockHelpers";
import { DUPLICATE_PROFILE_DETAIL } from "./patientProfileErrors";
import {
  MOCK_ACCOUNT_ID,
  type ClinicPatientRecord,
} from "./patientProfileMockData";
import {
  findClinicRecord,
  listClinicRecords,
  nextPatientCode,
  nextPatientId,
  saveClinicRecord,
} from "./patientProfileMockStore";

// Production gọi API hồ sơ; kho giả phía dưới chỉ phục vụ các kiểm thử hiện có.

/** Thông tin hành chính đầy đủ cho màn chi tiết. Quyền sửa theo lượt khám. */
export function toProfileDetail(
  record: ClinicPatientRecord,
  relationship: CareRelationship,
): PatientProfileDetail {
  return {
    id: record.id,
    patientCode: record.patientCode,
    relationship,
    fullName: record.fullName,
    dateOfBirth: record.dateOfBirth,
    sex: record.sex,
    phone: record.phone,
    address: record.address,
    citizenId: record.citizenId,
    email: record.email,
    hasVisits: record.hasVisits,
    editableFields: record.hasVisits
      ? [...EDITABLE_FIELDS_AFTER_VISIT]
      : [...EDITABLE_FIELDS_BEFORE_VISIT],
    allowedActions: ["update", "unlink", "editHistory"],
  };
}

/** POST /api/v1/me/patient-profiles: tạo hồ sơ cho người chưa từng khám và gắn ngay. */
export async function createPatientProfile(
  body: CreateProfileRequest,
): Promise<PatientProfileDetail> {
  if (process.env.NODE_ENV !== "test")
    return apiRequest<PatientProfileDetail>("/api/v1/me/patient-profiles", {
      method: "POST",
      body: JSON.stringify(body),
    });
  await mockLatency();
  throwFieldErrors(
    validateProfileFields(valuesFromRequest(body), CREATE_PROFILE_FIELDS),
  );
  const linked = getLinkedProfileRecords();
  if (
    body.relationship === "SELF" &&
    linked.some((item) => item.relationship === "SELF")
  )
    mockFail(409, "MSG-21");

  const phone = normalizePhone(body.phone);
  const duplicated = listClinicRecords().some(
    (record) =>
      normalizePhone(record.phone) === phone &&
      record.dateOfBirth === body.dateOfBirth,
  );
  if (duplicated) throw new ApiError(409, { detail: DUPLICATE_PROFILE_DETAIL });

  const record: ClinicPatientRecord = {
    id: nextPatientId(),
    patientCode: nextPatientCode(),
    fullName: body.fullName.trim(),
    dateOfBirth: body.dateOfBirth,
    sex: body.sex,
    phone,
    address: body.address.trim(),
    citizenId: body.citizenId?.trim() || null,
    email: body.email?.trim() || null,
    hasVisits: false,
    selfAccountId: body.relationship === "SELF" ? MOCK_ACCOUNT_ID : null,
  };
  saveClinicRecord(record);
  replaceLinkedProfilesMock([
    ...linked,
    toLinkedProfile(record, body.relationship),
  ]);
  return toProfileDetail(record, body.relationship);
}

/** GET /api/v1/me/patient-profiles/{id}. Hồ sơ không gắn với tài khoản → 404 MSG-41. */
export async function getPatientProfile(
  profileId: string,
  signal?: AbortSignal,
): Promise<PatientProfileDetail> {
  if (process.env.NODE_ENV !== "test") {
    const profile = await getAdministrativeProfile(
      profileId,
      true,
      signal ?? new AbortController().signal,
    );
    if (profile.patientProfileId !== profileId)
      throw new ApiError(404, { code: "MSG-21" });
    return {
      id: profile.patientProfileId,
      patientCode: "",
      relationship: "SELF",
      fullName: profile.fullName,
      dateOfBirth: profile.dateOfBirth,
      sex: profile.gender as PatientProfileDetail["sex"],
      phone: profile.phoneCanonical ?? "",
      address: profile.address,
      citizenId: profile.nationalId,
      email: null,
      hasVisits: false,
      editableFields: [...EDITABLE_FIELDS_BEFORE_VISIT],
      allowedActions: ["update", "editHistory"],
    };
  }
  await mockLatency(signal);
  const entry = linkedEntryOrFail(profileId);
  const record = findClinicRecord(profileId) ?? mockFail(404, "MSG-41");
  return toProfileDetail(record, entry.relationship);
}

function applyUpdate(
  record: ClinicPatientRecord,
  body: UpdateProfileRequest,
): ClinicPatientRecord {
  const next: ClinicPatientRecord = { ...record };
  if (body.fullName !== undefined) next.fullName = body.fullName.trim();
  if (body.dateOfBirth !== undefined) next.dateOfBirth = body.dateOfBirth;
  if (body.sex !== undefined) next.sex = body.sex;
  if (body.phone !== undefined) next.phone = normalizePhone(body.phone);
  if (body.address !== undefined) next.address = (body.address ?? "").trim();
  if (body.citizenId !== undefined)
    next.citizenId = body.citizenId?.trim() || null;
  if (body.email !== undefined) next.email = body.email?.trim() || null;
  return next;
}

/**
 * PATCH /api/v1/me/patient-profiles/{id}: chỉ trường trong `editableFields`. Gửi trường bị
 * khóa (họ tên, ngày sinh của hồ sơ đã khám) → 400 MSG-17 tại trường đó.
 */
export async function updatePatientProfile(
  profileId: string,
  body: UpdateProfileRequest,
): Promise<PatientProfileDetail> {
  if (process.env.NODE_ENV !== "test")
    return apiRequest<PatientProfileDetail>(
      `/api/v1/me/patient-profiles/${encodeURIComponent(profileId)}`,
      {
        method: "PATCH",
        body: JSON.stringify(body),
      },
    );
  await mockLatency();
  const entry = linkedEntryOrFail(profileId);
  const record = findClinicRecord(profileId) ?? mockFail(404, "MSG-41");
  const editable = toProfileDetail(record, entry.relationship).editableFields;

  const provided = EDITABLE_FIELDS_BEFORE_VISIT.filter(
    (field) => body[field] !== undefined,
  );
  rejectFields(provided.filter((field) => !editable.includes(field)));
  throwFieldErrors(validateProfileFields(valuesFromRequest(body), provided));

  const next = applyUpdate(record, body);
  saveClinicRecord(next);
  replaceLinkedProfilesMock(
    getLinkedProfileRecords().map((item) =>
      item.id === profileId ? toLinkedProfile(next, item.relationship) : item,
    ),
  );
  return toProfileDetail(next, entry.relationship);
}
