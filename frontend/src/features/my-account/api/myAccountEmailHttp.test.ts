import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { getMyAccount } from "./myAccountApi";
import {
  requestMyEmailChange,
  verifyMyEmailChange,
} from "./myAccountProfileApi";
import { ACCOUNT_ID, patientResponse } from "./myAccountTestFixtures";
import { MY_ACCOUNT_CHANGED } from "./myAccountEvents";

vi.mock("@/lib/auth/authSession", async (original) => ({
  ...(await original<typeof import("@/lib/auth/authSession")>()),
  peekSession: () => ({ accountId: ACCOUNT_ID }),
  getAccessToken: async () => "test-access-token",
  getCsrfToken: async () => "test-csrf-token",
}));
const fetchMock = vi.fn();
beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal("fetch", fetchMock);
});
afterEach(() => vi.unstubAllGlobals());

it("HTTP thật: GET -> POST 202 -> GET vẫn email cũ -> verify 200 mới đổi email", async () => {
  const old = patientResponse({ email: "old@example.com" });
  const updated = patientResponse({ email: "new@example.com" });
  fetchMock
    .mockResolvedValueOnce(Response.json(old))
    .mockResolvedValueOnce(
      Response.json({ message: "MSG-06" }, { status: 202 }),
    )
    .mockResolvedValueOnce(Response.json(old))
    .mockResolvedValueOnce(Response.json(updated));
  const changed = vi.fn();
  window.addEventListener(MY_ACCOUNT_CHANGED, changed);
  try {
    expect(await getMyAccount()).toMatchObject({ email: "old@example.com" });
    await requestMyEmailChange("new@example.com");
    const [path, init] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect(path).toContain("/api/v1/me/email-change");
    expect(init.method).toBe("POST");
    expect(init.body).toBe(JSON.stringify({ newEmail: "new@example.com" }));
    expect(new Headers(init.headers).get("Authorization")).toBe(
      "Bearer test-access-token",
    );
    expect(new Headers(init.headers).get("X-CSRF-TOKEN")).toBe(
      "test-csrf-token",
    );
    expect(changed).not.toHaveBeenCalled();
    expect(await getMyAccount()).toMatchObject({ email: "old@example.com" });
    expect(await verifyMyEmailChange("123456")).toMatchObject({
      email: "new@example.com",
    });
    expect(changed).toHaveBeenCalledTimes(1);
  } finally {
    window.removeEventListener(MY_ACCOUNT_CHANGED, changed);
  }
});
