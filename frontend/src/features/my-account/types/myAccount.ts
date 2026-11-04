import type { ActorRole } from "@/lib/domain/clinicRoles";

// Tài khoản của chính người đang đăng nhập (GET /api/v1/me). Hai biến thể theo `kind`:
// STAFF (nhân viên và quản trị, đăng nhập bằng tên đăng nhập) và PATIENT (bệnh nhân, đăng
// nhập bằng email). FE chỉ vẽ nút có trong `allowedActions`; quyền thật do backend kiểm.

export type MyAccountAction =
  | "updateAvatar"
  | "removeAvatar"
  | "changePassword"
  | "updateDisplayName"
  | "changeEmail";

// Giá trị trạng thái khớp phase 02 (accounts): ACTIVE | INACTIVE.
export type MyAccountStatus = "ACTIVE" | "INACTIVE";

interface MyAccountBase {
  /** Có trong response KAN-29; optional để giữ adapter ngoài giai đoạn 1. */
  mustChangePassword?: boolean;
  id: string;
  /** Tên hiển thị (R3 UC03: tên hiển thị, email, ảnh đại diện). */
  displayName: string;
  avatarUrl: string | null;
  status: MyAccountStatus;
  allowedActions: MyAccountAction[];
}

export interface StaffAccount extends MyAccountBase {
  kind: "STAFF";
  /** Tên đăng nhập của nhân viên; không dùng email để đăng nhập. */
  username: string;
  /** Mọi vai trò phòng khám trừ bệnh nhân. */
  role: Exclude<ActorRole, "PATIENT">;
  /** Đang dùng mật khẩu khởi tạo: phải đổi trước khi dùng tiếp. */
  mustChangePassword: boolean;
}

export interface PatientAccount extends MyAccountBase {
  kind: "PATIENT";
  /** Email đã xác minh; đổi qua OTP gửi tới email mới (BR-05). */
  email: string;
}

export type MyAccount = StaffAccount | PatientAccount;

export interface ChangePasswordInput {
  currentPassword: string;
  newPassword: string;
}

/** Projection transport KAN-29; không đồng nhất role ứng dụng với claim PostgreSQL. */
export interface MyAccountResponse {
  id: string;
  accountType:
    | "PATIENT"
    | "ADMIN"
    | "OWNER"
    | "DOCTOR"
    | "RECEPTIONIST"
    | "LAB_TECHNICIAN"
    | "IMAGING_TECHNICIAN";
  email: string;
  displayName: string;
  kind: "PATIENT" | "STAFF";
  username: string;
  role: ActorRole;
  avatarUrl: string | null;
  mustChangePassword: boolean;
  status: MyAccountStatus;
  allowedActions: string[];
}
