import type { CareRelationship } from "@/lib/domain/careRelationship";

/** Giới tính theo BE (enum UPPER_SNAKE). */
export type PatientSex = "MALE" | "FEMALE";

/** Hành động BE cho phép trên một hồ sơ. FE chỉ vẽ nút có trong danh sách này. */
export type ProfileAction = "update" | "unlink" | "editHistory";

/** Trường hành chính của hồ sơ. BE trả `editableFields` cho biết trường nào được sửa. */
export type EditableProfileField =
  | "fullName"
  | "dateOfBirth"
  | "sex"
  | "phone"
  | "address"
  | "citizenId"
  | "email";

/** GET /api/v1/me/patient-profiles/{id}: thông tin hành chính của một hồ sơ đã gắn. */
export interface PatientProfileDetail {
  id: string;
  patientCode: string;
  relationship: CareRelationship;
  fullName: string;
  /** YYYY-MM-DD */
  dateOfBirth: string;
  sex: PatientSex;
  phone: string;
  address: string | null;
  citizenId: string | null;
  email: string | null;
  hasVisits: boolean;
  editableFields: EditableProfileField[];
  allowedActions: ProfileAction[];
}

/**
 * Hồ sơ tại phòng khám có email trùng email đã xác minh, chưa gắn với tài khoản. Chỉ lộ
 * thông tin đủ để người dùng nhận ra: họ tên, năm sinh, mã bệnh nhân, giới tính.
 */
export interface SuggestedProfile {
  id: string;
  patientCode: string;
  fullName: string;
  birthYear: number;
  sex: PatientSex;
}

/** POST /api/v1/me/patient-profiles/links */
export interface LinkProfileRequest {
  patientId: string;
  relationship: CareRelationship;
}

/** Loại giấy tờ định danh theo BE (`identityDocumentType`). */
export type IdentityDocumentType = "CCCD" | "PERSONAL_ID" | "PASSPORT";

/**
 * Thân POST /api/v1/me/dependents (BE AddDependentRequest): họ tên, ngày sinh, giới tính,
 * quan hệ (không gồm SELF), số định danh kèm loại giấy tờ (không bắt buộc, có số thì phải có loại).
 */
export interface CreateProfileRequest {
  relationship: CareRelationship;
  fullName: string;
  dateOfBirth: string;
  sex: PatientSex;
  citizenId?: string;
  identityDocumentType?: IdentityDocumentType;
}

/** Thân JSON thật của POST /api/v1/me/dependents (BE AddDependentRequest). */
export interface AddDependentBody {
  fullName: string;
  dateOfBirth: string;
  gender: PatientSex;
  nationalId?: string;
  identityDocumentType?: IdentityDocumentType;
  relationship: CareRelationship;
}

/** Kết quả thêm người thân: gắn xong, hoặc cần xác thực thêm (OTP email / xác nhận tại quầy). */
export type AddDependentResult =
  | { status: "LINKED"; profileId: string }
  | { status: "OTP_SENT"; linkId: string; maskedEmail?: string }
  | { status: "COUNTER_REQUIRED" };

/** PATCH /api/v1/me/patient-profiles/{id}: chỉ các trường trong `editableFields`. */
export type UpdateProfileRequest = Partial<
  Pick<PatientProfileDetail, EditableProfileField>
>;

/** Thân POST/PATCH hồ sơ ở dạng chung, để lớp API giả kiểm tra như BE. */
export type ProfileRequestInput = Partial<
  Pick<PatientProfileDetail, EditableProfileField | "relationship">
>;

export type MedicalHistoryField =
  "conditions" | "allergies" | "procedures" | "injuries" | "orthopedicHistory";

export type MedicalHistoryTexts = Record<MedicalHistoryField, string>;

/** GET/PUT /api/v1/patient-profiles/{id}/medical-history. */
export interface MedicalHistory extends MedicalHistoryTexts {
  /** ISO có offset +07:00; null khi chưa ghi lần nào. */
  updatedAt: string | null;
  updatedBy: string | null;
  version: number;
}

/** PUT: các mục và `version` đang xem. Lệch version → 409 STALE_VERSION. */
export interface MedicalHistoryUpdate extends MedicalHistoryTexts {
  version: number;
}
