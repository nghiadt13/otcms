import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getCsrfToken, clearLocalAuthSession } from "@/lib/auth/authSession";
import { requestPasswordReset, resetPassword } from "./passwordRecoveryApi";

vi.mock("@/lib/auth/authSession", () => ({
  getCsrfToken: vi.fn(),
  clearLocalAuthSession: vi.fn(),
}));
vi.mock("@/lib/auth/currentAccountApi", () => ({
  resetCurrentAccountCache: vi.fn(),
}));
const fetchMock = vi.fn();
beforeEach(() => {
  vi.clearAllMocks();
  vi.stubGlobal("fetch", fetchMock);
  vi.mocked(getCsrfToken).mockResolvedValue("csrf-test");
});
afterEach(() => vi.unstubAllGlobals());
const failure = (code: string, status = 400) =>
  new Response(JSON.stringify({ error: { code } }), { status });

describe("password recovery API contract", () => {
  it("sends anonymous email with CSRF and cookies, accepting the generic text response", async () => {
    fetchMock.mockResolvedValue(
      new Response("If the account is eligible...", { status: 202 }),
    );
    await requestPasswordReset(" user@example.test ");
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/api/auth/patient/forgot-password"),
      expect.objectContaining({
        credentials: "include",
        cache: "no-store",
        method: "POST",
        body: '{"email":"user@example.test"}',
        headers: {
          "Content-Type": "application/json",
          "X-CSRF-TOKEN": "csrf-test",
        },
      }),
    );
  });
  it("sends token and unchanged password to the selected portal and handles empty 204", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    await resetPassword("token-test", " new-password-123 ", "staff");
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/api/auth/staff/reset-password"),
      expect.objectContaining({
        body: JSON.stringify({
          token: "token-test",
          newPassword: " new-password-123 ",
        }),
      }),
    );
    expect(clearLocalAuthSession).toHaveBeenCalledOnce();
  });
  it("refreshes a stale CSRF token once without requiring login", async () => {
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 403 }))
      .mockResolvedValueOnce(new Response("accepted", { status: 202 }));
    await requestPasswordReset("user@example.test");
    expect(getCsrfToken).toHaveBeenNthCalledWith(1, false);
    expect(getCsrfToken).toHaveBeenNthCalledWith(2, true);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
  it.each([
    ["PASSWORD_RESET_TOKEN_INVALID", 400, "invalid-token"],
    ["PASSWORD_REUSE_NOT_ALLOWED", 400, "password-reuse"],
    ["VALIDATION_FAILED", 400, "validation"],
    ["RATE_LIMIT_EXCEEDED", 429, "rate-limit"],
    ["STAFF_NETWORK_ACCESS_DENIED", 403, "staff-network"],
  ])("maps %s safely", async (code, status, reason) => {
    fetchMock.mockResolvedValue(failure(code, status));
    await expect(
      resetPassword("token-test", "new-password-123"),
    ).rejects.toMatchObject({ reason });
    expect(fetchMock).toHaveBeenCalledOnce();
    expect(clearLocalAuthSession).not.toHaveBeenCalled();
  });
  it("does not report success on network failure or unexpected 200 responses", async () => {
    fetchMock.mockRejectedValueOnce(new Error("offline"));
    await expect(
      requestPasswordReset("user@example.test"),
    ).rejects.toMatchObject({ reason: "service" });
    fetchMock.mockResolvedValue(new Response("unexpected", { status: 200 }));
    await expect(
      resetPassword("token", "new-password-123"),
    ).rejects.toMatchObject({ reason: "service" });
  });
  it("rejects a missing token without making a network request", async () => {
    await expect(resetPassword("", "new-password-123")).rejects.toMatchObject({
      reason: "invalid-token",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
