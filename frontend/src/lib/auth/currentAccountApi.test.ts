import { beforeEach, describe, expect, it, vi } from "vitest";

const { apiRequest, getSession, peekSession } = vi.hoisted(() => ({
  apiRequest: vi.fn(),
  getSession: vi.fn(),
  peekSession: vi.fn(),
}));

// Ranh giới ngoài được giả: lời gọi backend và đọc phiên; logic nhớ/gộp lời gọi chạy thật.
vi.mock("@/lib/api/client", () => ({ apiRequest }));
vi.mock("./authSession", () => ({ getSession, peekSession }));

import {
  fetchCurrentAccount,
  peekCurrentAccount,
  resetCurrentAccountCache,
} from "./currentAccountApi";

const account = {
  id: "11111111-1111-1111-1111-111111111111",
  accountType: "PATIENT",
  email: "an@example.com",
  displayName: "Nguyễn Văn An",
};
const session = { accountId: account.id };

beforeEach(() => {
  resetCurrentAccountCache();
  apiRequest.mockReset();
  getSession.mockReset();
  peekSession.mockReset();
});

describe("fetchCurrentAccount", () => {
  it("chưa đăng nhập thì trả null và không gọi backend", async () => {
    getSession.mockResolvedValue(null);
    expect(await fetchCurrentAccount()).toBeNull();
    expect(apiRequest).not.toHaveBeenCalled();
  });

  it("đã đăng nhập thì đọc GET /api/v1/me", async () => {
    getSession.mockResolvedValue(session);
    apiRequest.mockResolvedValue(account);
    expect(await fetchCurrentAccount()).toEqual(account);
    expect(apiRequest).toHaveBeenCalledWith("/api/v1/me");
  });

  it("gộp lời gọi đồng thời và nhớ kết quả cho cùng tài khoản", async () => {
    getSession.mockResolvedValue(session);
    apiRequest.mockResolvedValue(account);
    await Promise.all([fetchCurrentAccount(), fetchCurrentAccount()]);
    await fetchCurrentAccount();
    expect(apiRequest).toHaveBeenCalledTimes(1);
  });

  it("đổi tài khoản thì đọc lại", async () => {
    getSession.mockResolvedValue(session);
    apiRequest.mockResolvedValue(account);
    await fetchCurrentAccount();
    getSession.mockResolvedValue({
      accountId: "22222222-2222-2222-2222-222222222222",
    });
    apiRequest.mockResolvedValue({ ...account, displayName: "Người khác" });
    expect((await fetchCurrentAccount())?.displayName).toBe("Người khác");
    expect(apiRequest).toHaveBeenCalledTimes(2);
  });

  it("lỗi backend được ném ra và lần sau thử lại", async () => {
    getSession.mockResolvedValue(session);
    apiRequest.mockRejectedValueOnce(new Error("boom"));
    await expect(fetchCurrentAccount()).rejects.toThrow("boom");
    apiRequest.mockResolvedValue(account);
    expect(await fetchCurrentAccount()).toEqual(account);
  });
});

describe("peekCurrentAccount", () => {
  it("chưa nhớ thì null; đã nhớ và phiên còn thì trả ngay không gọi mạng", async () => {
    peekSession.mockReturnValue(session);
    expect(peekCurrentAccount()).toBeNull();
    getSession.mockResolvedValue(session);
    apiRequest.mockResolvedValue(account);
    await fetchCurrentAccount();
    expect(peekCurrentAccount()).toEqual(account);
    expect(apiRequest).toHaveBeenCalledTimes(1);
  });

  it("phiên đã mất hoặc đổi tài khoản thì null", async () => {
    getSession.mockResolvedValue(session);
    apiRequest.mockResolvedValue(account);
    await fetchCurrentAccount();
    peekSession.mockReturnValue(null);
    expect(peekCurrentAccount()).toBeNull();
    peekSession.mockReturnValue({
      accountId: "22222222-2222-2222-2222-222222222222",
    });
    expect(peekCurrentAccount()).toBeNull();
  });
});
