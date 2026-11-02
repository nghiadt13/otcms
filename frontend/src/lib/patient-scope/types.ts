import type { CareRelationship } from "@/lib/domain/careRelationship";

/** Hồ sơ bệnh nhân đã gắn với tài khoản đang đăng nhập: bản thân hoặc người thân. */
export interface LinkedProfile {
  id: string;
  patientCode: string;
  fullName: string;
  /** YYYY-MM-DD */
  dateOfBirth: string;
  sex: "MALE" | "FEMALE";
  relationship: CareRelationship;
  /** R3: hồ sơ đang chờ xác minh chưa được đọc tiền sử. */
  linkStatus?: "CONFIRMED" | "PENDING_OTP" | "PENDING_COUNTER";
}

/** Hình trả về của GET /api/v1/me/patient-profiles. */
export interface LinkedProfileList {
  items: LinkedProfile[];
}
