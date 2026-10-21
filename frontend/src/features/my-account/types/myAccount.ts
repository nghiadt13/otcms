import type { ActorRole } from "@/lib/domain/clinicRoles";

// Tài khoản của chính người đang đăng nhập (GET /api/v1/me).

export type MyAccountAction = "updateAvatar" | "removeAvatar" | "changePassword";

// Giá trị trạng thái khớp phase 02 (accounts): ACTIVE | INACTIVE.
export type MyAccountStatus = "ACTIVE" | "INACTIVE";

export interface MyAccount {
  id: string;
  /** Tên đăng nhập; Admin không có email. */
  username: string;
  role: Extract<ActorRole, "ADMIN">;
  status: MyAccountStatus;
  avatarUrl: string | null;
  /** Đang dùng mật khẩu khởi tạo: phải đổi trước khi dùng tiếp. */
  mustChangePassword: boolean;
  /** FE chỉ vẽ nút có trong mảng này. */
  allowedActions: MyAccountAction[];
}

export interface ChangePasswordInput {
  currentPassword: string;
  newPassword: string;
}
