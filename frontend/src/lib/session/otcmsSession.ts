"use client";

const STORAGE_KEY = "otcms.auth.session";

export type OtcmsSession = {
  accessToken: string;
  expiresAt: number;
  accountId: string;
  accountType: string;
};

export function saveOtcmsSession(session: OtcmsSession): void {
  if (typeof window === "undefined") return;
  window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function getOtcmsAccessToken(): string | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const session = JSON.parse(raw) as OtcmsSession;
    if (!session.accessToken || session.expiresAt <= Date.now()) {
      clearOtcmsSession();
      return null;
    }
    return session.accessToken;
  } catch {
    clearOtcmsSession();
    return null;
  }
}

export function clearOtcmsSession(): void {
  if (typeof window === "undefined") return;
  window.sessionStorage.removeItem(STORAGE_KEY);
}
