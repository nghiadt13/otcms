import type { AuthMessageCode } from "../authMessages";

// Thông tin Guest nhập ở bước 1 của UC06 (R3 sau sửa 23/10): họ tên (tên hiển thị), email,
// mật khẩu. Ngày sinh, giới tính, SĐT thuộc hồ sơ bệnh nhân (UC13), không có ở đây.
export interface PatientRegistrationInput {
  fullName: string;
  email: string;
  password: string;
  confirmPassword: string;
  // Đồng ý cho phòng khám xử lý dữ liệu cá nhân và sức khỏe (bắt buộc).
  consent: boolean;
}

// Lỗi nghiệp vụ trả từ lớp API: chỉ mang mã MSG để màn hình hiển thị đúng nội
// dung chung, không mang chi tiết kiểu "email đã tồn tại".
export class AuthRequestError extends Error {
  constructor(public readonly code: AuthMessageCode) {
    super(code);
    this.name = "AuthRequestError";
  }
}

// Đổi lỗi bất kỳ thành mã MSG; lỗi không rõ nguồn coi là lỗi dịch vụ (MSG-04).
export function toAuthMessageCode(cause: unknown): AuthMessageCode {
  return cause instanceof AuthRequestError ? cause.code : "MSG-04";
}
