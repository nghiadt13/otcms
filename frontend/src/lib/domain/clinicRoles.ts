// Vai trò cố định theo SRS. Đặt ở lib/domain vì nhiều feature dùng (feature không import lẫn nhau).
export type StaffRole =
  | "RECEPTIONIST"
  | "ORTHOPEDIC_DOCTOR"
  | "IMAGING_TECHNICIAN"
  | "LAB_TECHNICIAN";

export type AccountType = StaffRole | "PATIENT";

/** Người thực hiện ở nhật ký (14) và khu cá nhân (15). */
export type ActorRole = AccountType | "ADMIN" | "CLINIC_OWNER";

export const ROLE_LABELS: Record<ActorRole, string> = {
  RECEPTIONIST: "Lễ tân",
  ORTHOPEDIC_DOCTOR: "Bác sĩ chỉnh hình",
  IMAGING_TECHNICIAN: "KTV chẩn đoán hình ảnh",
  LAB_TECHNICIAN: "KTV xét nghiệm",
  PATIENT: "Bệnh nhân",
  ADMIN: "Quản trị viên",
  CLINIC_OWNER: "Chủ phòng khám",
};

/** Loại tài khoản Admin được quản lý (không có Admin, Owner). */
export const ACCOUNT_TYPE_LABELS: Record<AccountType, string> = {
  RECEPTIONIST: ROLE_LABELS.RECEPTIONIST,
  ORTHOPEDIC_DOCTOR: ROLE_LABELS.ORTHOPEDIC_DOCTOR,
  IMAGING_TECHNICIAN: ROLE_LABELS.IMAGING_TECHNICIAN,
  LAB_TECHNICIAN: ROLE_LABELS.LAB_TECHNICIAN,
  PATIENT: ROLE_LABELS.PATIENT,
};

export const STAFF_ROLES: StaffRole[] = [
  "RECEPTIONIST",
  "ORTHOPEDIC_DOCTOR",
  "IMAGING_TECHNICIAN",
  "LAB_TECHNICIAN",
];
