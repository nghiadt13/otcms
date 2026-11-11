import { afterEach, describe, expect, it, vi } from "vitest";
import { apiRequest, publicApiRequest } from "@/lib/api/client";
import { isApiError } from "@/lib/api/apiError";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";

// Chỉ giả phần xác thực; đường gọi thật là apiRequest/publicApiRequest → toApiError.
vi.mock("@/lib/auth/authSession", () => ({
  getAccessToken: async () => "access-token",
  getCsrfToken: async () => "csrf-token",
  refreshSession: async () => null,
}));

/** Thân lỗi đúng hình `ErrorResponse` của backend: `{ error: { code, message, ... } }`. */
const beError = (code: string) => ({
  error: {
    code,
    message: "Lỗi thử nghiệm",
    timestamp: "2026-11-11T02:00:00Z",
    correlationId: "test-1",
  },
});

/** Cho fetch trả về `status` + `body`, rồi trả về lỗi mà client ném ra. */
async function failureWith(
  status: number,
  body: unknown,
  call: () => Promise<unknown> = () => apiRequest("/api/v1/exam-queue"),
): Promise<unknown> {
  vi.stubGlobal(
    "fetch",
    vi.fn(
      async () =>
        new Response(JSON.stringify(body), {
          status,
          headers: { "Content-Type": "application/json" },
        }),
    ),
  );
  return call().then(
    () => undefined,
    (cause: unknown) => cause,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("client: đọc error.code của backend thành mã MSG", () => {
  it("EXAM_FEE_UNPAID hiển thị đúng MSG-37, không rơi về MSG-04", async () => {
    const failure = await failureWith(409, beError("EXAM_FEE_UNPAID"));
    expect(isApiError(failure)).toBe(true);
    expect(messageFromError(failure)).toBe(formatAppMessage("MSG-37"));
    expect(messageFromError(failure)).not.toBe(formatAppMessage("MSG-04"));
  });

  it("giữ nguyên errorCode thô để màn hình tự xử lý", async () => {
    const failure = await failureWith(409, beError("EXAM_FEE_UNPAID"));
    expect(isApiError(failure) && failure.errorCode).toBe("EXAM_FEE_UNPAID");
  });

  it("error.code đã là mã MSG (BE trả MSG-36) thì dùng nguyên mã đó", async () => {
    const failure = await failureWith(409, beError("MSG-36"));
    expect(isApiError(failure) && failure.code).toBe("MSG-36");
    expect(messageFromError(failure)).toBe(formatAppMessage("MSG-36"));
  });

  it("mã không có trong bảng vẫn rơi về MSG-04 như trước", async () => {
    const failure = await failureWith(503, beError("SOME_UNKNOWN_CODE"));
    expect(isApiError(failure) && failure.code).toBeUndefined();
    expect(messageFromError(failure)).toBe(formatAppMessage("MSG-04"));
  });

  it("mã MSG cấp cao nhất (nếu có) được ưu tiên hơn error.code", async () => {
    const failure = await failureWith(409, {
      code: "MSG-30",
      error: { code: "EXAM_FEE_UNPAID" },
    });
    expect(isApiError(failure) && failure.code).toBe("MSG-30");
  });

  it("publicApiRequest cũng đọc error.code như apiRequest", async () => {
    const failure = await failureWith(
      409,
      beError("VISIT_EXAM_STATE_CONFLICT"),
      () => publicApiRequest("/api/v1/exam-queue"),
    );
    expect(messageFromError(failure)).toBe(formatAppMessage("MSG-42"));
  });
});
