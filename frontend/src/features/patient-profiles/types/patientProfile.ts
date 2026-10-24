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

/**
 * POST /api/v1/me/patient-profiles. `address` bắt buộc theo đặc tả §2 và bố cục màn (§3);
 * §6 ghi `address?` là chưa chốt, xem báo cáo.
 */
export interface CreateProfileRequest {
  relationship: CareRelationship;
  fullName: string;
  dateOfBirth: string;
  sex: PatientSex;
  phone: string;
  address: string;
  citizenId?: string;
  email?: string;
}

/** PATCH /api/v1/me/patient-profiles/{id}: chỉ các trường trong `editableFields`. */
export type UpdateProfileRequest = Partial<
  Pick<PatientProfileDetail, EditableProfileField>
>;

/** Thân POST/PATCH hồ sơ ở dạng chung, để lớp API giả kiểm tra như BE. */
export type ProfileRequestInput = Partial<
  Pick<PatientProfileDetail, EditableProfileField | "relationship">
>;

export type MedicalHistoryField =
  | "conditions"
  | "allergies"
  | "procedures"
  | "injuries"
  | "orthopedicHistory";

export type MedicalHistoryTexts = Record<MedicalHistoryField, string>;

/** GET/PUT /api/v1/patients/{id}/medical-history. */
export interface MedicalHistory extends MedicalHistoryTexts {
  /** ISO có offset +07:00; null khi chưa ghi lần nào. */
  updatedAt: string | null;
  updatedBy: string | null;
  version: number;
}

/** PUT: các mục và `version` đang xem. Lệch version → 409 STALE. */
export interface MedicalHistoryUpdate extends MedicalHistoryTexts {
  version: number;
}
