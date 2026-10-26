import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  AuthSessionError,
  getAccessToken,
  getSession,
  resetAuthSessionForTest,
  signInWithPassword,
  signOut,
} from "./authSession";

// Chỉ giả `fetch` (ranh giới với backend); logic phiên, CSRF và refresh chạy thật.
const fetchMock = vi.fn();

const tokenBody = (accessToken: string, expiresIn = 900) => ({
  accessToken,
  expiresIn,
  accountId: "acc-1",
  accountType: "PATIENT",
});

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function callsTo(path: string) {
  return fetchMock.mock.calls.filter(([url]) => String(url).endsWith(path));
}

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockReset();
  resetAuthSessionForTest();
  window.localStorage.clear();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("signInWithPassword", () => {
  it("gọi cổng bệnh nhân kèm cookie và giữ access token trong bộ nhớ", async () => {
    fetchMock.mockResolvedValueOnce(json(tokenBody("jwt-1")));
    const session = await signInWithPassword("patient", " an@x.com ", "pw");

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toMatch(/\/api\/auth\/patient\/login$/);
    expect(init.credentials).toBe("include");
    expect(JSON.parse(init.body)).toEqual({
      email: "an@x.com",
      password: "pw",
    });
    expect(session.accessToken).toBe("jwt-1");
    expect(await getAccessToken()).toBe("jwt-1");
    expect(window.localStorage.getItem("otcms.auth.hasSession")).toBe("1");
    // Access token không bao giờ nằm trong bộ nhớ cục bộ.
    expect(JSON.stringify({ ...window.localStorage })).not.toContain("jwt-1");
  });

  it("401 ném AuthSessionError mang mã HTTP", async () => {
    fetchMock.mockResolvedValueOnce(json({}, 401));
    await expect(signInWithPassword("staff", "a@x.com", "bad")).rejects.toThrow(
      new AuthSessionError(401),
    );
  });

  it("lỗi mạng ném AuthSessionError(0)", async () => {
    fetchMock.mockRejectedValueOnce(new TypeError("network"));
    await expect(
      signInWithPassword("patient", "a@x.com", "pw"),
    ).rejects.toMatchObject({ status: 0 });
  });
});

describe("getSession", () => {
  it("khách chưa từng đăng nhập thì không gọi backend", async () => {
    expect(await getSession()).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("access token hết hạn thì refresh bằng CSRF, và nhiều lời gọi đồng thời chỉ refresh một lần", async () => {
    fetchMock.mockResolvedValueOnce(json(tokenBody("old", 1))); // login: hết hạn ngay (trong biên 30s)
    await signInWithPassword("patient", "a@x.com", "pw");
    fetchMock.mockImplementation(async (url: string) =>
      String(url).endsWith("/csrf")
        ? json({ csrfToken: "csrf-1" })
        : json(tokenBody("new")),
    );

    const [a, b] = await Promise.all([getAccessToken(), getAccessToken()]);

    expect(a).toBe("new");
    expect(b).toBe("new");
    expect(callsTo("/api/auth/refresh")).toHaveLength(1);
    expect(callsTo("/api/auth/refresh")[0][1].headers["X-CSRF-TOKEN"]).toBe(
      "csrf-1",
    );
  });

  it("refresh bị 401 thì xoá phiên và trả null", async () => {
    window.localStorage.setItem("otcms.auth.hasSession", "1");
    fetchMock.mockImplementation(async (url: string) =>
      String(url).endsWith("/csrf") ? json({ csrfToken: "c" }) : json({}, 401),
    );
    expect(await getSession()).toBeNull();
    expect(window.localStorage.getItem("otcms.auth.hasSession")).toBeNull();
    expect(await getSession()).toBeNull();
    expect(callsTo("/api/auth/refresh")).toHaveLength(1);
  });

  it("refresh bị 403 (CSRF lệch) thì lấy CSRF mới và thử lại đúng một lần", async () => {
    window.localStorage.setItem("otcms.auth.hasSession", "1");
    let refreshCalls = 0;
    fetchMock.mockImplementation(async (url: string) => {
      if (String(url).endsWith("/csrf")) return json({ csrfToken: "c" });
      refreshCalls += 1;
      return refreshCalls === 1 ? json({}, 403) : json(tokenBody("fresh"));
    });
    expect(await getAccessToken()).toBe("fresh");
    expect(callsTo("/api/auth/csrf")).toHaveLength(2);
    expect(refreshCalls).toBe(2);
  });
});

describe("signOut", () => {
  it("huỷ phiên ở backend kèm CSRF rồi xoá trạng thái, kể cả khi backend lỗi", async () => {
    fetchMock.mockResolvedValueOnce(json(tokenBody("jwt-1")));
    await signInWithPassword("patient", "a@x.com", "pw");
    fetchMock.mockImplementation(async (url: string) =>
      String(url).endsWith("/csrf") ? json({ csrfToken: "c" }) : json({}, 500),
    );

    await signOut();

    expect(callsTo("/api/auth/logout")).toHaveLength(1);
    expect(window.localStorage.getItem("otcms.auth.hasSession")).toBeNull();
    expect(await getSession()).toBeNull();
  });
});
