import { saveOtcmsSession } from "@/lib/session/otcmsSession";
import { AuthRequestError, type PatientRegistrationInput } from "../types/auth";

// UC01, UC06 gọi backend Spring Boot; UC02 vẫn là API giả trong frontend.
const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

const wait = (ms = 400) => new Promise((resolve) => setTimeout(resolve, ms));

export async function signIn(email: string, password: string): Promise<void> {
  try {
    const response = await fetch(`${apiBaseUrl}/api/auth/patient/login`, {
      method: "POST",
      credentials: "include",
      cache: "no-store",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: email.trim(), password }),
    });
    if (!response.ok) {
      throw new AuthRequestError(response.status === 401 ? "MSG-01" : "MSG-04");
    }
    const session = (await response.json()) as {
      accessToken: string;
      expiresIn: number;
      accountId: string;
      accountType: string;
    };
    if (session.accountType !== "PATIENT" || !session.accessToken) {
      throw new AuthRequestError("MSG-04");
    }
    saveOtcmsSession({
      accessToken: session.accessToken,
      expiresAt: Date.now() + session.expiresIn * 1000,
      accountId: session.accountId,
      accountType: session.accountType,
    });
  } catch (cause) {
    if (cause instanceof AuthRequestError) throw cause;
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

// --- UC06: xác thực email qua backend ----------------------------------

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
