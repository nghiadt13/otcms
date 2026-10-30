import { apiBaseUrl } from "@/lib/api/apiBaseUrl";
import { AuthSessionError, signInWithPassword } from "@/lib/auth/authSession";
import { fetchCurrentAccount } from "@/lib/auth/currentAccountApi";
import { AuthRequestError, type PatientRegistrationInput } from "../types/auth";

// Authentication, registration and password recovery call the Spring backend.
export { requestPasswordReset, resetPassword } from "./passwordRecoveryApi";

export async function signIn(email: string, password: string): Promise<void> {
  try {
    await signInWithPassword("patient", email, password);
  } catch (cause) {
    // 401 = sai thông tin hoặc tài khoản không đủ điều kiện (MSG-01); còn lại (mạng, 429, 5xx) là
    // lỗi dịch vụ (MSG-04). Không phân biệt lý do để không lộ tài khoản nào tồn tại (BR-03).
    const rejected = cause instanceof AuthSessionError && cause.status === 401;
    throw new AuthRequestError(rejected ? "MSG-01" : "MSG-04");
  }
  // Nạp sẵn tên người dùng từ backend để trang chủ hiện ngay ở header. Không chặn đăng nhập
  // nếu lần đọc này lỗi: header tự đọc lại và ghi log.
  await fetchCurrentAccount().catch((error: unknown) => {
    console.error("Không đọc được tài khoản sau đăng nhập", error);
  });
}

// Đăng nhập nội bộ (admin, chủ phòng khám, nhân viên) qua POST /api/auth/staff/login. Cùng quy tắc
// lỗi với `signIn`: 401 là MSG-01, còn lại là MSG-04.
export async function signInStaff(
  email: string,
  password: string,
): Promise<import("@/lib/auth/authSession").AuthSession> {
  try {
    return await signInWithPassword("staff", email, password);
  } catch (cause) {
    const rejected = cause instanceof AuthSessionError && cause.status === 401;
    throw new AuthRequestError(rejected ? "MSG-01" : "MSG-04");
  }
}

export async function signInAdministrator(
  username: string,
  password: string,
): Promise<import("@/lib/auth/authSession").AuthSession> {
  try {
    return await signInWithPassword("administrator", username, password);
  } catch (cause) {
    const rejected = cause instanceof AuthSessionError && cause.status === 401;
    throw new AuthRequestError(rejected ? "MSG-01" : "MSG-04");
  }
}

// --- UC06: đăng ký qua backend -------------------------------------------

// Ba endpoint này backend miễn CSRF và không cần phiên (SecurityConfig), nên gọi fetch thẳng,
// không đi qua `apiRequest`. Đăng ký xong chưa đăng nhập: người dùng đăng nhập ở bước sau.
async function registrationRequest(path: string, body: unknown): Promise<void> {
  let response: Response;
  try {
    response = await fetch(`${apiBaseUrl}${path}`, {
      method: "POST",
      cache: "no-store",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  } catch {
    throw new AuthRequestError("MSG-04");
  }
  if (response.ok) return;
  // 400 = dữ liệu không dùng được (email đã có, mã sai hoặc hết hạn): MSG-08 chung, không lộ lý do
  // (BR-03). Còn lại (429, 503, 5xx) là lỗi dịch vụ.
  throw new AuthRequestError(response.status === 400 ? "MSG-08" : "MSG-04");
}

/** POST /api/auth/patient/register (202). Ngày sinh, giới tính, SĐT thuộc hồ sơ nên không gửi ở đây. */
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

/** POST /api/auth/patient/verify-email (204). */
export async function verifyRegistrationEmail(
  email: string,
  code: string,
): Promise<void> {
  await registrationRequest("/api/auth/patient/verify-email", {
    email: email.trim(),
    otp: code,
  });
}

/** POST /api/auth/patient/resend-otp (202). */
export async function resendRegistrationCode(email: string): Promise<void> {
  await registrationRequest("/api/auth/patient/resend-otp", {
    email: email.trim(),
  });
}
