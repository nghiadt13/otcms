import { AuthSessionError, signInWithPassword } from "@/lib/auth/authSession";
import { AuthRequestError, type PatientRegistrationInput } from "../types/auth";

// UC01 Đăng nhập đi thật qua backend (POST /api/auth/patient/login). UC02 và UC06 hiện là LỚP API GIẢ vì
// backend chưa có endpoint: dữ liệu nằm trong bộ nhớ trình duyệt, mất khi tải
// lại. Khi nối backend, giữ nguyên chữ ký hàm và chỉ thay phần thân; component
// không cần đổi. Mọi lỗi ném ra là `AuthRequestError` mang mã MSG.

const wait = (ms = 400) => new Promise((resolve) => setTimeout(resolve, ms));

export async function signIn(email: string, password: string): Promise<void> {
  try {
    await signInWithPassword("patient", email, password);
  } catch (cause) {
    // 401 = sai thông tin hoặc tài khoản không đủ điều kiện (MSG-01); còn lại (mạng, 429, 5xx) là
    // lỗi dịch vụ (MSG-04). Không phân biệt lý do để không lộ tài khoản nào tồn tại (BR-03).
    const rejected = cause instanceof AuthSessionError && cause.status === 401;
    throw new AuthRequestError(rejected ? "MSG-01" : "MSG-04");
  }
}

// --- UC02: API giả -------------------------------------------------------

// Luôn thành công để không lộ email có tồn tại hay không (BR-03).
export async function requestPasswordReset(email: string): Promise<void> {
  await wait();
  void email;
}

// `token` lấy từ link trong email. API giả coi token rỗng là xác nhận sai.
export async function resetPassword(
  token: string,
  newPassword: string,
): Promise<void> {
  await wait();
  if (!token) throw new AuthRequestError("MSG-04");
  void newPassword;
}

// --- UC06: API giả -------------------------------------------------------

// Mã "000000" mô phỏng mã sai/hết hạn; mã 6 số khác được chấp nhận.
const REJECTED_CODE = "000000";
const pendingRegistrations = new Set<string>();

export async function registerPatient(
  input: PatientRegistrationInput,
): Promise<void> {
  await wait();
  // Khi nối backend: gửi `fullName`, `email`, `password` (không gửi `confirmPassword`,
  // `consent`); phía Supabase là `auth.signUp(email, password, { data: { displayName } })`
  // rồi gửi OTP. Ngày sinh, giới tính, SĐT không gửi ở đây (thuộc hồ sơ, UC13).
  // Backend thật kiểm tra trùng khóa định danh (BR-11) và trả MSG-08 chung (BR-03).
  pendingRegistrations.add(input.email.trim().toLowerCase());
}

export async function verifyRegistrationEmail(
  email: string,
  code: string,
): Promise<void> {
  await wait();
  const key = email.trim().toLowerCase();
  if (!pendingRegistrations.has(key) || code === REJECTED_CODE)
    throw new AuthRequestError("MSG-08");
  pendingRegistrations.delete(key);
}

export async function resendRegistrationCode(email: string): Promise<void> {
  await wait(250);
  void email;
}
