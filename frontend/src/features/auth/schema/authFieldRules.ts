import type { PatientRegistrationInput } from "../types/auth";
import { validateDateOfBirth } from "./dateOfBirth";

// Kiểm tra phía trình duyệt chỉ để báo lỗi sớm; backend/credential authority vẫn
// kiểm tra lại (BR-04, BR-10, BR-16). Chưa chọn thư viện form (xem AGENTS.md) nên
// giữ hàm thuần, trả về thông báo ngắn cho từng trường.

// Chính sách mật khẩu tạm (BR-04 chưa chốt cụ thể). Đổi ở đây là đổi cho cả
// đăng ký, đặt lại mật khẩu và gợi ý hiển thị.
export const passwordPolicy = [
  {
    id: "length",
    label: "Tối thiểu 8 ký tự",
    test: (v: string) => v.length >= 8,
  },
  {
    id: "letter",
    label: "Có ít nhất một chữ cái",
    test: (v: string) => /\p{L}/u.test(v),
  },
  {
    id: "digit",
    label: "Có ít nhất một chữ số",
    test: (v: string) => /\d/.test(v),
  },
] as const;

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const phonePattern = /^(0|\+84)\d{9}$/;

export function validateEmail(value: string): string | undefined {
  if (!value.trim()) return "Nhập email.";
  if (!emailPattern.test(value.trim())) return "Email chưa đúng định dạng.";
}

export function validatePassword(value: string): string | undefined {
  if (!value) return "Nhập mật khẩu.";
  if (!passwordPolicy.every((rule) => rule.test(value)))
    return "Mật khẩu chưa đạt yêu cầu bên dưới.";
}

export function validatePasswordConfirmation(
  password: string,
  confirmation: string,
): string | undefined {
  if (!confirmation) return "Nhập lại mật khẩu.";
  if (password !== confirmation) return "Mật khẩu nhập lại không khớp.";
}

export function validateVerificationCode(value: string): string | undefined {
  if (!/^\d{6}$/.test(value)) return "Nhập đủ 6 chữ số trong email.";
}

export type RegistrationErrors = Partial<
  Record<keyof PatientRegistrationInput, string>
>;

export function validateRegistration(
  input: PatientRegistrationInput,
  today = new Date(),
): RegistrationErrors {
  const errors: RegistrationErrors = {};
  const name = input.fullName.trim();
  if (!name) errors.fullName = "Nhập họ và tên.";
  else if (name.length < 2) errors.fullName = "Họ và tên quá ngắn.";

  errors.dateOfBirth = validateDateOfBirth(input.dateOfBirth, today);

  if (!input.gender) errors.gender = "Chọn giới tính.";

  const phone = input.phone.replace(/[\s.-]/g, "");
  if (!phone) errors.phone = "Nhập số điện thoại.";
  else if (!phonePattern.test(phone))
    errors.phone = "Số điện thoại gồm 10 số, bắt đầu bằng 0.";

  errors.email = validateEmail(input.email);
  errors.password = validatePassword(input.password);
  errors.confirmPassword = validatePasswordConfirmation(
    input.password,
    input.confirmPassword,
  );
  if (!input.consent) errors.consent = "Bạn cần đồng ý để tạo tài khoản.";

  // Bỏ các khóa không có lỗi để `Object.keys(errors).length` dùng được.
  for (const key of Object.keys(errors) as (keyof RegistrationErrors)[])
    if (!errors[key]) delete errors[key];
  return errors;
}
