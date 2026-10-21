import type { MyAccount } from "../types/myAccount";

export const MOCK_MY_ACCOUNT: MyAccount = {
  id: "6f1c2a9e-3b7d-4e8a-9c21-5d0a7b3e4f10",
  username: "admin.hoa",
  role: "ADMIN",
  status: "ACTIVE",
  avatarUrl: null,
  mustChangePassword: false,
  allowedActions: ["updateAvatar", "changePassword"],
};

/** Tài khoản vừa được cấp, còn dùng mật khẩu khởi tạo. */
export const MOCK_MY_ACCOUNT_FIRST_LOGIN: MyAccount = {
  ...MOCK_MY_ACCOUNT,
  mustChangePassword: true,
};

/** Mật khẩu hiện tại đúng trong mock. */
export const MOCK_CORRECT_CURRENT_PASSWORD = "MatKhauCu1";
/** Mật khẩu hiện tại này kích hoạt lỗi 400 ở ô mật khẩu hiện tại. */
export const MOCK_WRONG_CURRENT_PASSWORD = "SaiMatKhau1";
/** Mật khẩu hiện tại này kích hoạt lỗi dịch vụ 500. */
export const MOCK_SERVICE_ERROR_PASSWORD = "LoiDichVu1";

export const AVATAR_ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp"];
export const AVATAR_MAX_BYTES = 2 * 1024 * 1024;
