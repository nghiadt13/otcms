import { apiBaseUrl } from "@/lib/api/apiBaseUrl";
import { getCsrfToken, clearLocalAuthSession } from "@/lib/auth/authSession";
import { resetCurrentAccountCache } from "@/lib/auth/currentAccountApi";
import { AuthRequestError } from "../types/auth";

export type RecoveryPortal = "patient" | "staff";
export type RecoveryFailure =
  | "invalid-token"
  | "password-reuse"
  | "validation"
  | "rate-limit"
  | "staff-network"
  | "service";

export class PasswordRecoveryError extends AuthRequestError {
  constructor(public readonly reason: RecoveryFailure) {
    super(
      reason === "password-reuse" || reason === "validation"
        ? "MSG-08"
        : "MSG-04",
    );
  }
}

async function errorCode(response: Response): Promise<string | undefined> {
  try {
    const body = await response.json();
    return body?.error?.code;
  } catch {
    return undefined;
  }
}

// Recovery is anonymous, but still requires the backend's CSRF cookie/header pair.
// Do not use apiRequest: it requires an authenticated session before sending.
async function recoveryRequest(
  portal: RecoveryPortal,
  action: "forgot-password" | "reset-password",
  body: unknown,
): Promise<void> {
  for (let attempt = 0; attempt < 2; attempt++) {
    let response: Response;
    try {
      response = await fetch(`${apiBaseUrl}/api/auth/${portal}/${action}`, {
        method: "POST",
        credentials: "include",
        cache: "no-store",
        headers: {
          "Content-Type": "application/json",
          "X-CSRF-TOKEN": await getCsrfToken(attempt > 0),
        },
        body: JSON.stringify(body),
      });
    } catch {
      throw new PasswordRecoveryError("service");
    }
    if (response.status === (action === "forgot-password" ? 202 : 204)) return;
    const code = await errorCode(response);
    if (code === "STAFF_NETWORK_ACCESS_DENIED")
      throw new PasswordRecoveryError("staff-network");
    // Spring's anonymous CSRF rejection may be 401/403 with an empty body.
    if (
      (response.status === 401 || response.status === 403) &&
      (!code || code === "CSRF_VALIDATION_FAILED") &&
      attempt === 0
    )
      continue;
    if (response.status === 429) throw new PasswordRecoveryError("rate-limit");
    if (code === "PASSWORD_RESET_TOKEN_INVALID")
      throw new PasswordRecoveryError("invalid-token");
    if (code === "PASSWORD_REUSE_NOT_ALLOWED")
      throw new PasswordRecoveryError("password-reuse");
    if (code === "VALIDATION_FAILED")
      throw new PasswordRecoveryError("validation");
    throw new PasswordRecoveryError("service");
  }
  throw new PasswordRecoveryError("service");
}

export async function requestPasswordReset(
  email: string,
  portal: RecoveryPortal = "patient",
): Promise<void> {
  await recoveryRequest(portal, "forgot-password", { email: email.trim() });
}

export async function resetPassword(
  token: string,
  newPassword: string,
  portal: RecoveryPortal = "patient",
): Promise<void> {
  if (!token.trim()) throw new PasswordRecoveryError("invalid-token");
  await recoveryRequest(portal, "reset-password", { token, newPassword });
  // Backend revoked refresh sessions; discard the browser's cached identity too.
  clearLocalAuthSession();
  resetCurrentAccountCache();
}
