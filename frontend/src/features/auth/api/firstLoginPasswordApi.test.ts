import { beforeEach, afterEach, expect, it, vi } from "vitest";
import { getSession, getCsrfToken, clearLocalAuthSession } from "@/lib/auth/authSession";
import { resetCurrentAccountCache } from "@/lib/auth/currentAccountApi";
import { changeFirstLoginPassword } from "./firstLoginPasswordApi";

vi.mock("@/lib/auth/authSession", () => ({ getSession: vi.fn(), getCsrfToken: vi.fn(), clearLocalAuthSession: vi.fn() }));
vi.mock("@/lib/auth/currentAccountApi", () => ({ resetCurrentAccountCache: vi.fn() }));
const fetchMock = vi.fn();
beforeEach(() => { vi.clearAllMocks(); vi.stubGlobal("fetch", fetchMock); vi.mocked(getSession).mockResolvedValue({ accessToken: "jwt-fixture", accountType: "ADMIN" } as Awaited<ReturnType<typeof getSession>>); vi.mocked(getCsrfToken).mockResolvedValue("csrf-fixture"); });
afterEach(() => vi.unstubAllGlobals());

it("changes the initial password with authorization and CSRF, then discards the revoked session", async () => {
  fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
  await changeFirstLoginPassword("initial-pass", "new-password-123");
  expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining("/api/auth/change-password"), expect.objectContaining({
    method: "POST", credentials: "include", body: '{"currentPassword":"initial-pass","newPassword":"new-password-123"}',
    headers: expect.objectContaining({ Authorization: "Bearer jwt-fixture", "X-CSRF-TOKEN": "csrf-fixture" }),
  }));
  expect(clearLocalAuthSession).toHaveBeenCalledOnce();
  expect(resetCurrentAccountCache).toHaveBeenCalledOnce();
});
it("keeps the form usable on wrong current password and does not clear the session", async () => {
  fetchMock.mockResolvedValue(new Response(JSON.stringify({ error: { code: "CURRENT_PASSWORD_INVALID" } }), { status: 400 }));
  await expect(changeFirstLoginPassword("wrong", "new-password-123")).rejects.toMatchObject({ reason: "current" });
  expect(clearLocalAuthSession).not.toHaveBeenCalled();
});
it("requires a live session before sending a credential", async () => {
  vi.mocked(getSession).mockResolvedValue(null);
  await expect(changeFirstLoginPassword("old", "new-password-123")).rejects.toMatchObject({ reason: "session" });
  expect(fetchMock).not.toHaveBeenCalled();
});
