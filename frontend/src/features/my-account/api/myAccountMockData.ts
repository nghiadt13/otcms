import type { PatientAccount, StaffAccount } from "../types/myAccount";

// Dữ liệu giả của `GET /api/v1/me`. Tài khoản bệnh nhân `acc-an` khớp `getMockCurrentActor("patient")`.

export const MOCK_MY_ACCOUNT: StaffAccount = {
  id: "6f1c2a9e-3b7d-4e8a-9c21-5d0a7b3e4f10",
  kind: "STAFF",
  displayName: "Lê Thị Hoa",
  username: "admin.hoa",
  role: "ADMIN",
  status: "ACTIVE",
  avatarUrl: null,
  mustChangePassword: false,
  allowedActions: ["updateAvatar", "changePassword"],
};

/** Tài khoản vừa được cấp, còn dùng mật khẩu khởi tạo. */
export const MOCK_MY_ACCOUNT_FIRST_LOGIN: StaffAccount = {
  ...MOCK_MY_ACCOUNT,
  mustChangePassword: true,
};

export const MOCK_PATIENT_ACCOUNT: PatientAccount = {
  id: "acc-an",
  kind: "PATIENT",
  displayName: "Nguyễn Văn An",
  email: "an@example.com",
  status: "ACTIVE",
  avatarUrl: null,
  allowedActions: [
    "updateAvatar",
    "changePassword",
    "updateDisplayName",
    "changeEmail",
  ],
};

/** Mật khẩu hiện tại đúng trong mock. */
export const MOCK_CORRECT_CURRENT_PASSWORD = "MatKhauCu1";
/** Mật khẩu hiện tại này kích hoạt lỗi 400 ở ô mật khẩu hiện tại. */
export const MOCK_WRONG_CURRENT_PASSWORD = "SaiMatKhau1";
/** Mật khẩu hiện tại này kích hoạt lỗi dịch vụ 500. */
export const MOCK_SERVICE_ERROR_PASSWORD = "LoiDichVu1";

/** Mã OTP đúng khi xác minh email mới trong mock; mã khác là sai. */
export const MOCK_EMAIL_CHANGE_OTP = "123456";
/** Email đã thuộc tài khoản khác: đổi sang đó trả MSG-08. */
export const MOCK_TAKEN_EMAIL = "taken@example.com";

export const AVATAR_ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp"];
export const AVATAR_MAX_BYTES = 2 * 1024 * 1024;
