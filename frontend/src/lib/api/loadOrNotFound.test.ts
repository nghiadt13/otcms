import { describe, expect, it, vi } from "vitest";
import { ApiError } from "./apiError";
import { loadOrNotFound } from "./loadOrNotFound";

// `notFound()` của Next ném lỗi đặc biệt; ở đây chỉ cần thấy nó được gọi.
vi.mock("next/navigation", () => ({
  notFound: () => {
    throw new Error("NEXT_NOT_FOUND");
  },
}));

describe("loadOrNotFound", () => {
  it("trả dữ liệu khi lời gọi thành công", async () => {
    await expect(loadOrNotFound(async () => "ok")).resolves.toBe("ok");
  });

  it("404 chuyển thành notFound()", async () => {
    await expect(
      loadOrNotFound(async () => {
        throw new ApiError(404, { code: "MSG-50" });
      }),
    ).rejects.toThrow("NEXT_NOT_FOUND");
  });

  it("lỗi khác (ví dụ 500) được ném tiếp để error boundary xử lý", async () => {
    const failure = new ApiError(500, { code: "MSG-04" });
    await expect(
      loadOrNotFound(async () => {
        throw failure;
      }),
    ).rejects.toBe(failure);
  });
});
