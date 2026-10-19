export type UserRole =
  | "patient"
  | "receptionist"
  | "orthopedic_doctor"
  | "radiology_technician"
  | "admin";

/** Nhân viên phòng khám mà quản trị viên được phép tạo và đổi vai trò theo BR-11 và BR-12. */
export type StaffRole =
  | "receptionist"
  | "orthopedic_doctor"
  | "radiology_technician";

export type AccountStatus = "active" | "inactive" | "pending_invitation";

export interface UserAccount {
  id: string;
  fullName: string;
  email: string;
  phone: string;
  role: UserRole;
  status: AccountStatus;
  createdAt: string;
  /** ISO string hoặc chuỗi ngày giờ để tính relative time và hiển thị tooltip. */
  lastSignInAt?: string | null;
  /** Thông tin khi tài khoản bị tạm khóa (BR-15). */
  lockReason?: string | null;
  lockedBy?: string | null;
  lockedAt?: string | null;
}

export interface CreateStaffInput {
  fullName: string;
  email: string;
  phone: string;
  role: StaffRole;
}

export const ROLE_LABELS: Record<UserRole, string> = {
  patient: "Bệnh nhân",
  receptionist: "Lễ tân",
  orthopedic_doctor: "Bác sĩ chỉnh hình",
  radiology_technician: "KTV chẩn đoán hình ảnh",
  admin: "Quản trị viên",
};

export const STAFF_ROLE_OPTIONS: {
  value: StaffRole;
  label: string;
  description: string;
}[] = [
  {
    value: "orthopedic_doctor",
    label: "Bác sĩ chỉnh hình",
    description: "Khám, chẩn đoán, kê đơn và thực hiện thủ thuật chuyên khoa.",
  },
  {
    value: "receptionist",
    label: "Lễ tân",
    description: "Tiếp đón bệnh nhân, xếp hàng đợi, thu ngân và điều phối lịch hẹn.",
  },
  {
    value: "radiology_technician",
    label: "KTV chẩn đoán hình ảnh",
    description: "Thực hiện chụp X-quang, MRI, CT và tải dữ liệu lên DICOM.",
  },
];

export const STATUS_LABELS: Record<AccountStatus, string> = {
  active: "Đang hoạt động",
  inactive: "Tạm khóa",
  pending_invitation: "Chờ kích hoạt",
};
