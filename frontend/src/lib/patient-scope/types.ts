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
}

/** Hình trả về của GET /api/v1/me/patient-profiles. */
export interface LinkedProfileList {
  items: LinkedProfile[];
}
