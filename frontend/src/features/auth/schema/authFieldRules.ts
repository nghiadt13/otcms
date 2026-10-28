import { passwordPolicy } from "@/lib/domain/passwordPolicy";
import type { PatientRegistrationInput } from "../types/auth";

// Kiểm tra phía trình duyệt chỉ để báo lỗi sớm; backend/credential authority vẫn
// kiểm tra lại (BR-04, BR-05). Chưa chọn thư viện form (xem AGENTS.md) nên
// giữ hàm thuần, trả về thông báo ngắn cho từng trường.

// Chính sách mật khẩu (BR-04) nằm ở lib/domain/passwordPolicy.ts; re-export để
// các import cũ trong auth vẫn chạy.
export { passwordPolicy };

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const localDemoEmailPattern = /^[^\s@]+@localhost$/i;

export function validateEmail(value: string): string | undefined {
  const normalized = value.trim();
  if (!normalized) return "Nhập email.";
  if (
    !emailPattern.test(normalized) &&
    !(process.env.NODE_ENV !== "production" &&
      localDemoEmailPattern.test(normalized))
  )
    return "Email chưa đúng định dạng.";
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
): RegistrationErrors {
  const errors: RegistrationErrors = {};
  const name = input.fullName.trim();
  if (!name) errors.fullName = "Nhập họ và tên.";
  else if (name.length < 2) errors.fullName = "Họ và tên quá ngắn.";

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
