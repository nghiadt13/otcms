import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// Ranh giới ngoài: HTTP và phiên đăng nhập.
const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest }));
const session = vi.hoisted(() => ({
  getAccessToken: vi.fn(),
  getCsrfToken: vi.fn(),
  peekSession: vi.fn(),
  peekCsrfToken: vi.fn(),
}));
vi.mock("@/lib/auth/authSession", () => session);

import {
  cancelPendingHold,
  clearCancelKeepalive,
  getAppointmentStatus,
  primeCancelKeepalive,
  sendCancelKeepalive,
} from "./pendingHoldLiveApi";

beforeEach(() => {
  apiRequest.mockReset();
  session.getAccessToken.mockReset().mockResolvedValue("access-token");
  session.getCsrfToken.mockReset().mockResolvedValue("csrf-token");
  // Mặc định bộ nhớ authSession chưa có gì: gửi dùng bản chụp lúc prime.
  session.peekSession.mockReset().mockReturnValue(null);
  session.peekCsrfToken.mockReset().mockReturnValue(null);
  clearCancelKeepalive();
});
afterEach(() => vi.restoreAllMocks());

describe("cancelPendingHold", () => {
  it("POST /pending-hold/cancel không body và trả kết quả của BE", async () => {
    apiRequest.mockResolvedValue({ cancelledCount: 2, alreadyPaid: false });

    const result = await cancelPendingHold();

    expect(result).toEqual({ cancelledCount: 2, alreadyPaid: false });
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/v1/appointments/pending-hold/cancel",
      { method: "POST", signal: undefined },
    );
  });

  it("alreadyPaid được giữ nguyên", async () => {
    apiRequest.mockResolvedValue({ cancelledCount: 0, alreadyPaid: true });
    expect(await cancelPendingHold()).toEqual({
      cancelledCount: 0,
      alreadyPaid: true,
    });
  });

  it("phản hồi thiếu trường: về 0 / false thay vì NaN hay undefined", async () => {
    apiRequest.mockResolvedValue({});
    expect(await cancelPendingHold()).toEqual({
      cancelledCount: 0,
      alreadyPaid: false,
    });
  });

  it("lỗi API được ném tiếp cho nơi gọi", async () => {
    apiRequest.mockRejectedValue(new Error("boom"));
    await expect(cancelPendingHold()).rejects.toThrow("boom");
  });
});

describe("getAppointmentStatus", () => {
  it("GET /appointments/{id} và chỉ trả status thô của BE", async () => {
    apiRequest.mockResolvedValue({ id: "a/1", status: "AWAITING_PAYMENT" });

    expect(await getAppointmentStatus("a/1")).toBe("AWAITING_PAYMENT");
    expect(apiRequest).toHaveBeenCalledWith("/api/v1/appointments/a%2F1", {
      signal: undefined,
    });
  });
});

describe("fetch keepalive khi đóng tab", () => {
  it("chưa lấy token: không gửi", () => {
    const fetchMock = vi.spyOn(globalThis, "fetch");
    expect(sendCancelKeepalive()).toBe(false);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("đã lấy token: POST keepalive kèm Authorization, X-CSRF-TOKEN và cookie", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 200 }));
    await primeCancelKeepalive();

    expect(sendCancelKeepalive()).toBe(true);

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toMatch(/\/api\/v1\/appointments\/pending-hold\/cancel$/);
    expect(init).toMatchObject({
      method: "POST",
      keepalive: true,
      credentials: "include",
      headers: {
        Authorization: "Bearer access-token",
        "X-CSRF-TOKEN": "csrf-token",
      },
    });
  });

  it("lúc gửi dùng token MỚI NHẤT trong bộ nhớ, không dùng bản chụp cũ (CSRF đã đổi sau khi tạo hóa đơn/QR)", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 200 }));
    await primeCancelKeepalive();
    session.peekCsrfToken.mockReturnValue("csrf-moi");
    session.peekSession.mockReturnValue({ accessToken: "access-moi" });

    sendCancelKeepalive();

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(init.headers).toMatchObject({
      Authorization: "Bearer access-moi",
      "X-CSRF-TOKEN": "csrf-moi",
    });
  });

  it("clear khi prime còn đang bay: kết quả prime bị bỏ, không gán lại token", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch");
    let release: (token: string) => void = () => undefined;
    session.getAccessToken.mockReturnValue(
      new Promise<string>((resolve) => {
        release = resolve;
      }),
    );
    const priming = primeCancelKeepalive();

    clearCancelKeepalive();
    release("access-muon");
    await priming;

    expect(sendCancelKeepalive()).toBe(false);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("chưa đăng nhập hoặc lấy token lỗi: không gửi, không ném", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch");
    session.getAccessToken.mockResolvedValue(null);
    await primeCancelKeepalive();
    expect(sendCancelKeepalive()).toBe(false);

    session.getAccessToken.mockRejectedValue(new Error("mất mạng"));
    await primeCancelKeepalive();
    expect(sendCancelKeepalive()).toBe(false);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("fetch bị từ chối (preflight, mạng): không ném lỗi chưa xử lý", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValue(new TypeError("blocked"));
    await primeCancelKeepalive();
    expect(sendCancelKeepalive()).toBe(true);
    await new Promise((resolve) => setTimeout(resolve, 5));
  });
});
