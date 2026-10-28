import { AuthSessionError, signInWithPassword } from "@/lib/auth/authSession";
import { apiBaseUrl } from "@/lib/api/apiBaseUrl";
import { AuthRequestError, type PatientRegistrationInput } from "../types/auth";

// Đăng nhập và đăng ký gọi backend Spring Boot; yêu cầu đặt lại mật khẩu hiện vẫn là API giả.

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

// Đăng nhập nội bộ (admin, chủ phòng khám, nhân viên) qua POST /api/auth/staff/login. Cùng quy tắc
// lỗi với `signIn`: 401 là MSG-01, còn lại là MSG-04.
export async function signInStaff(
  email: string,
  password: string,
): Promise<void> {
  try {
    await signInWithPassword("staff", email, password);
  } catch (cause) {
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

// --- UC06: đăng ký/xác minh qua backend --------------------------------

async function registrationRequest(path: string, body: unknown): Promise<void> {
  let response: Response;
  try {
    response = await fetch(`${apiBaseUrl}${path}`, {
      method: "POST",
      credentials: "include",
      cache: "no-store",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  } catch {
    throw new AuthRequestError("MSG-04");
  }
  if (response.ok) return;
  throw new AuthRequestError(response.status === 400 ? "MSG-08" : "MSG-04");
}

export async function registerPatient(
  input: PatientRegistrationInput,
): Promise<void> {
  await registrationRequest("/api/auth/patient/register", {
    fullName: input.fullName.trim(),
    email: input.email.trim(),
    password: input.password,
    confirmPassword: input.confirmPassword,
    patientConsent: input.consent,
  });
}

export async function verifyRegistrationEmail(
  email: string,
  code: string,
): Promise<void> {
  await registrationRequest("/api/auth/patient/verify-email", {
    email: email.trim(),
    otp: code,
  });
}

export async function resendRegistrationCode(email: string): Promise<void> {
  await registrationRequest("/api/auth/patient/resend-otp", {
    email: email.trim(),
  });
}
