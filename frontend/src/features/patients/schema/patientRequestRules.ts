import type {
  CreateWalkInPatientRequest,
  PatientLookupQuery,
  PatientSearchMode,
  Sex,
} from "../types/patient";
import {
  PATIENT_FIELD_HINTS,
  isSex,
  isValidAddress,
  isValidBirthDate,
  isValidCitizenId,
  isValidEmail,
  isValidFullName,
  isValidPatientCode,
  isValidPhone,
  normalizeCitizenId,
  normalizeFullName,
  normalizePatientCode,
  normalizePhone,
  parseDateOfBirth,
  type PatientFieldErrors,
} from "./patientFieldRules";

// Kiểm tra toàn bộ body/query theo hợp đồng API. Form dùng để chặn trước khi gửi; lớp API giả
// dùng để trả 400 MSG-17 đúng hình BE. Một nguồn luật cho cả hai.

/** Trạng thái form tìm, trước khi chuẩn hóa. */
export interface LookupFormInput {
  mode: PatientSearchMode;
  phone: string;
  /** dd/mm/yyyy như người dùng gõ */
  dateOfBirth: string;
  patientCode: string;
  fullName: string;
  /** Lần tìm trước trả MULTIPLE: ô họ tên bắt buộc và được gửi kèm. */
  askFullName: boolean;
}

/** Body đã chuẩn hóa một phần, trước khi biết ngày sinh và giới tính hợp lệ hay không. */
export interface WalkInDraft {
  fullName: string;
  /** YYYY-MM-DD hoặc chuỗi rỗng khi chưa đọc được */
  dateOfBirth: string;
  sex: string;
  phone: string;
  address: string;
  citizenId: string | null;
  email?: string;
}

/** Giá trị các ô của form tạo hồ sơ, đúng như người dùng đã gõ. */
export interface WalkInFormValues {
  fullName: string;
  /** dd/mm/yyyy */
  dateOfBirth: string;
  sex: string;
  phone: string;
  address: string;
  citizenId: string;
  email: string;
}

/** Lỗi của truy vấn tìm đã chuẩn hóa. Rỗng nghĩa là hợp lệ. */
export function findLookupFieldErrors(
  query: PatientLookupQuery,
): PatientFieldErrors {
  if ("patientCode" in query) {
    return isValidPatientCode(query.patientCode)
      ? {}
      : { patientCode: PATIENT_FIELD_HINTS.patientCode };
  }
  const found: PatientFieldErrors = {};
  if (!isValidPhone(query.phone)) found.phone = PATIENT_FIELD_HINTS.phone;
  if (!isValidBirthDate(query.dateOfBirth))
    found.dateOfBirth = PATIENT_FIELD_HINTS.dateOfBirth;
  if (query.fullName !== undefined && !isValidFullName(query.fullName))
    found.fullName = PATIENT_FIELD_HINTS.fullName;
  return found;
}

/** Lỗi của body tạo hồ sơ vãng lai. Rỗng nghĩa là hợp lệ. */
export function findWalkInFieldErrors(draft: WalkInDraft): PatientFieldErrors {
  const found: PatientFieldErrors = {};
  if (!isValidFullName(draft.fullName)) found.fullName = PATIENT_FIELD_HINTS.fullName;
  if (!isValidBirthDate(draft.dateOfBirth))
    found.dateOfBirth = PATIENT_FIELD_HINTS.dateOfBirth;
  if (!isSex(draft.sex)) found.sex = PATIENT_FIELD_HINTS.sex;
  if (!isValidPhone(draft.phone)) found.phone = PATIENT_FIELD_HINTS.phone;
  if (!isValidAddress(draft.address)) found.address = PATIENT_FIELD_HINTS.address;
  if (draft.citizenId !== null && !isValidCitizenId(draft.citizenId))
    found.citizenId = PATIENT_FIELD_HINTS.citizenId;
  if (draft.email !== undefined && !isValidEmail(draft.email))
    found.email = PATIENT_FIELD_HINTS.email;
  return found;
}

/** Form tìm → query gửi đi, hoặc lỗi theo ô. */
export function buildLookupQuery(
  input: LookupFormInput,
): { query: PatientLookupQuery } | { errors: PatientFieldErrors } {
  const query: PatientLookupQuery =
    input.mode === "code"
      ? { patientCode: normalizePatientCode(input.patientCode) }
      : {
          phone: normalizePhone(input.phone),
          dateOfBirth: parseDateOfBirth(input.dateOfBirth) ?? "",
          ...(input.askFullName
            ? { fullName: normalizeFullName(input.fullName) }
            : {}),
        };
  const errors = findLookupFieldErrors(query);
  return Object.keys(errors).length > 0 ? { errors } : { query };
}

/** Form tạo hồ sơ → body gửi đi, hoặc lỗi theo ô. Email rỗng thì bỏ khóa, CCCD rỗng thì null. */
export function buildWalkInRequest(
  values: WalkInFormValues,
): { request: CreateWalkInPatientRequest } | { errors: PatientFieldErrors } {
  const citizenId = normalizeCitizenId(values.citizenId);
  const email = values.email.trim();
  const draft: WalkInDraft = {
    fullName: normalizeFullName(values.fullName),
    dateOfBirth: parseDateOfBirth(values.dateOfBirth) ?? "",
    sex: values.sex,
    phone: normalizePhone(values.phone),
    address: values.address.trim(),
    citizenId: citizenId === "" ? null : citizenId,
    ...(email === "" ? {} : { email }),
  };
  const errors = findWalkInFieldErrors(draft);
  if (Object.keys(errors).length > 0 || !isSex(values.sex)) return { errors };
  const sex: Sex = values.sex;
  return { request: { ...draft, sex } };
}
