const STORAGE_KEY = "otcms.staff.session";

export type StaffSession = {
  accessToken: string;
  expiresAt: number;
  accountId: string;
  accountType: string;
};

export function saveStaffSession(session: StaffSession) {
  if (typeof window !== "undefined")
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function getStaffAccessToken(): string | null {
  if (typeof window === "undefined") return null;
  const raw = window.sessionStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    const session = JSON.parse(raw) as StaffSession;
    if (session.expiresAt <= Date.now()) {
      clearStaffSession();
      return null;
    }
    return session.accessToken;
  } catch {
    clearStaffSession();
    return null;
  }
}

export function clearStaffSession() {
  if (typeof window !== "undefined")
    window.sessionStorage.removeItem(STORAGE_KEY);
}
