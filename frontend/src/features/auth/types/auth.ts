import type { AuthMessageCode } from "../authMessages";

export type Gender = "male" | "female" | "other";

// Thông tin Guest nhập ở bước 1 của UC06 (danh tính, liên hệ, tài khoản).
export interface PatientRegistrationInput {
  fullName: string;
  dateOfBirth: string; // dd/mm/yyyy như người dùng nhập (xem schema/dateOfBirth)
  gender: Gender | "";
  phone: string;
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
