import { isAuthApiError } from "@supabase/supabase-js";
import { getSupabaseBrowserClient } from "@/lib/supabase/browser";
import { AuthRequestError, type PatientRegistrationInput } from "../types/auth";
import { saveStaffSession } from "@/lib/api/staffSession";

// UC01 Đăng nhập đi thật qua Supabase Auth. UC02 và UC06 hiện là LỚP API GIẢ vì
// backend chưa có endpoint: dữ liệu nằm trong bộ nhớ trình duyệt, mất khi tải
// lại. Khi nối backend, giữ nguyên chữ ký hàm và chỉ thay phần thân; component
// không cần đổi. Mọi lỗi ném ra là `AuthRequestError` mang mã MSG.

const wait = (ms = 400) => new Promise((resolve) => setTimeout(resolve, ms));

export async function signIn(email: string, password: string): Promise<void> {
  try {
    const { error } = await getSupabaseBrowserClient().auth.signInWithPassword({
      email: email.trim(),
      password,
    });
    if (error) throw error;
  } catch (cause) {
    // 4xx từ Supabase = sai thông tin hoặc tài khoản không đủ điều kiện (MSG-01);
    // còn lại (mạng, thiếu cấu hình, 5xx) là lỗi dịch vụ (MSG-04).
    const rejected =
      isAuthApiError(cause) && cause.status >= 400 && cause.status < 500;
    throw new AuthRequestError(rejected ? "MSG-01" : "MSG-04");
  }
}

/** Staff/admin credentials are verified by Spring Boot against the application DB. */
export async function signInStaff(email: string, password: string): Promise<void> {
  const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
  let response: Response;
  try {
    response = await fetch(`${apiBaseUrl}/api/auth/staff/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      cache: "no-store",
      body: JSON.stringify({ email: email.trim(), password }),
    });
  } catch {
    throw new AuthRequestError("MSG-04");
  }
  if (!response.ok) {
    throw new AuthRequestError(response.status === 401 ? "MSG-01" : "MSG-04");
  }
  try {
    const session = (await response.json()) as {
      accessToken: string;
      expiresIn: number;
      accountId: string;
      accountType: string;
    };
    if (!session.accessToken || !["STAFF", "ADMIN"].includes(session.accountType))
      throw new Error("Invalid staff session response");
    saveStaffSession({
      accessToken: session.accessToken,
      expiresAt: Date.now() + session.expiresIn * 1000,
      accountId: session.accountId,
      accountType: session.accountType,
    });
  } catch {
    throw new AuthRequestError("MSG-04");
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
  // Khi nối backend: gửi ngày sinh qua `dateOfBirthToIso` (yyyy-mm-dd), không
  // gửi `confirmPassword`.
  // Backend thật kiểm tra trùng khóa định danh và trả MSG-08 chung (BR-10).
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
