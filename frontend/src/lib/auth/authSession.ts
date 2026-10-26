import { apiBaseUrl } from "@/lib/api/apiBaseUrl";

// Phiên đăng nhập do backend (Spring) cấp, không qua Supabase Auth.
// - Access token (JWT, sống ngắn) chỉ nằm trong bộ nhớ của tab; không ghi localStorage để XSS không đọc được.
// - Refresh token nằm trong cookie httpOnly do backend đặt; JavaScript không đọc được, chỉ gửi kèm bằng
//   `credentials: "include"`.
// - /refresh và /logout cần CSRF: gửi token lấy từ GET /api/auth/csrf trong header X-CSRF-TOKEN.
// - Tải lại trang làm mất access token; `getAccessToken` tự lấy lại bằng refresh cookie.

// Khớp enum AccountType của backend.
export type AccountType =
  "PATIENT" | "RECEPTIONIST" | "DOCTOR" | "OWNER" | "ADMIN";
export type LoginKind = "patient" | "staff";

type SessionTokenBody = {
  accessToken: string;
  expiresIn: number;
  accountId: string;
  accountType: AccountType;
};

export type AuthSession = {
  accessToken: string;
  /** Mốc hết hạn (ms, đồng hồ trình duyệt). */
  expiresAt: number;
  accountId: string;
  accountType: AccountType;
};

/** Lỗi đăng nhập/phiên: `status` là mã HTTP, 0 là lỗi mạng. */
export class AuthSessionError extends Error {
  constructor(public readonly status: number) {
    super(`Xác thực thất bại (${status}).`);
    this.name = "AuthSessionError";
  }
}

// Làm mới trước khi hết hạn để request đang bay không bị 401.
const EXPIRY_MARGIN_MS = 30_000;
// Cờ "từng đăng nhập" để khách chưa đăng nhập không gọi refresh vô ích ở mỗi trang công khai.
// Không phải bí mật và không cấp quyền gì: quyền do cookie và backend quyết định.
const HINT_KEY = "otcms.auth.hasSession";

let current: AuthSession | null = null;
let csrfToken: string | null = null;
let refreshing: Promise<AuthSession | null> | null = null;

function readHint(): boolean {
  try {
    return window.localStorage.getItem(HINT_KEY) === "1";
  } catch {
    return false;
  }
}

function writeHint(value: boolean) {
  try {
    if (value) window.localStorage.setItem(HINT_KEY, "1");
    else window.localStorage.removeItem(HINT_KEY);
  } catch {
    // Trình duyệt chặn bộ nhớ cục bộ: bỏ qua, phiên vẫn dùng được trong tab này.
  }
}

function setSession(body: SessionTokenBody): AuthSession {
  current = {
    accessToken: body.accessToken,
    expiresAt: Date.now() + body.expiresIn * 1000,
    accountId: body.accountId,
    accountType: body.accountType,
  };
  writeHint(true);
  return current;
}

function clearSession() {
  current = null;
  csrfToken = null;
  writeHint(false);
}

async function post(path: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(`${apiBaseUrl}${path}`, {
      method: "POST",
      credentials: "include",
      cache: "no-store",
      ...init,
    });
  } catch {
    throw new AuthSessionError(0);
  }
}

async function loadCsrfToken(): Promise<string> {
  if (csrfToken) return csrfToken;
  let response: Response;
  try {
    response = await fetch(`${apiBaseUrl}/api/auth/csrf`, {
      credentials: "include",
      cache: "no-store",
    });
  } catch {
    throw new AuthSessionError(0);
  }
  if (!response.ok) throw new AuthSessionError(response.status);
  const body = (await response.json()) as { csrfToken: string };
  csrfToken = body.csrfToken;
  return csrfToken;
}

/** Đăng nhập email + mật khẩu qua backend; thành công thì giữ access token trong bộ nhớ. */
export async function signInWithPassword(
  kind: LoginKind,
  email: string,
  password: string,
): Promise<AuthSession> {
  const response = await post(`/api/auth/${kind}/login`, {
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: email.trim(), password }),
  });
  if (!response.ok) throw new AuthSessionError(response.status);
  csrfToken = null; // phiên mới: lấy lại CSRF khi cần
  return setSession((await response.json()) as SessionTokenBody);
}

async function requestRefresh(): Promise<AuthSession | null> {
  // Thử lại một lần: CSRF cũ có thể lệch cookie sau khi backend cấp lại.
  for (let attempt = 0; attempt < 2; attempt += 1) {
    const response = await post("/api/auth/refresh", {
      headers: { "X-CSRF-TOKEN": await loadCsrfToken() },
    });
    if (response.ok)
      return setSession((await response.json()) as SessionTokenBody);
    if (response.status === 403 && attempt === 0) {
      csrfToken = null;
      continue;
    }
    if (response.status === 401 || response.status === 403) {
      clearSession();
      return null;
    }
    throw new AuthSessionError(response.status);
  }
  return null;
}

/** Gộp nhiều lời gọi đồng thời thành một lần refresh (backend xoay refresh token mỗi lần). */
export function refreshSession(): Promise<AuthSession | null> {
  refreshing ??= requestRefresh().finally(() => {
    refreshing = null;
  });
  return refreshing;
}

/** Phiên hiện có: dùng bộ nhớ nếu còn hạn, nếu không thì refresh bằng cookie. `null` = chưa đăng nhập. */
export async function getSession(): Promise<AuthSession | null> {
  if (current && current.expiresAt - EXPIRY_MARGIN_MS > Date.now())
    return current;
  if (!current && !readHint()) return null;
  return refreshSession();
}

export async function getAccessToken(): Promise<string | null> {
  return (await getSession())?.accessToken ?? null;
}

/** Đăng xuất: huỷ phiên ở backend (bỏ qua lỗi) rồi xoá trạng thái trong tab. */
export async function signOut(): Promise<void> {
  try {
    await post("/api/auth/logout", {
      headers: { "X-CSRF-TOKEN": await loadCsrfToken() },
    });
  } catch {
    // Mất mạng hoặc phiên đã hết: vẫn xoá phiên phía trình duyệt.
  } finally {
    clearSession();
  }
}

/** Chỉ dùng trong test: đưa module về trạng thái chưa đăng nhập. */
export function resetAuthSessionForTest() {
  current = null;
  csrfToken = null;
  refreshing = null;
}
