import { apiBaseUrl } from "@/lib/api/apiBaseUrl";
import {
  clearLocalAuthSession,
  getCsrfToken,
  getSession,
} from "@/lib/auth/authSession";
import { resetCurrentAccountCache } from "@/lib/auth/currentAccountApi";

export class FirstLoginPasswordError extends Error {
  constructor(
    public readonly reason:
      "current" | "reused" | "invalid" | "session" | "service",
  ) {
    super(reason);
  }
}

export async function changeFirstLoginPassword(
  currentPassword: string,
  newPassword: string,
): Promise<void> {
  const session = await getSession();
  if (!session) throw new FirstLoginPasswordError("session");
  for (let attempt = 0; attempt < 2; attempt += 1) {
    let response: Response;
    try {
      response = await fetch(`${apiBaseUrl}/api/auth/change-password`, {
        method: "POST",
        credentials: "include",
        cache: "no-store",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${session.accessToken}`,
          "X-CSRF-TOKEN": await getCsrfToken(attempt > 0),
        },
        body: JSON.stringify({ currentPassword, newPassword }),
      });
    } catch {
      throw new FirstLoginPasswordError("service");
    }
    if (response.status === 204) {
      clearLocalAuthSession();
      resetCurrentAccountCache();
      return;
    }
    const body = (await response.json().catch(() => ({}))) as {
      error?: { code?: string };
    };
    const code = body.error?.code;
    if (code === "CURRENT_PASSWORD_INVALID")
      throw new FirstLoginPasswordError("current");
    if (code === "PASSWORD_REUSE_NOT_ALLOWED")
      throw new FirstLoginPasswordError("reused");
    if (code === "VALIDATION_FAILED")
      throw new FirstLoginPasswordError("invalid");
    if (
      (response.status === 401 || response.status === 403) &&
      (!code || code === "CSRF_VALIDATION_FAILED") &&
      attempt === 0
    )
      continue;
    if (response.status === 401 || code === "AUTHENTICATION_FAILED")
      throw new FirstLoginPasswordError("session");
    throw new FirstLoginPasswordError("service");
  }
}
