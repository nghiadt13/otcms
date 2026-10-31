import { describe, expect, it } from "vitest";
import { validateReviewReply } from "./reviewReplyFieldRules";

describe("validateReviewReply", () => {
  it("từ chối rỗng và chỉ khoảng trắng", () => {
    expect(validateReviewReply("")).toBe("MSG-57");
    expect(validateReviewReply("   \n ")).toBe("MSG-57");
  });
  it("nhận đúng 1000 ký tự, từ chối 1001", () => {
    expect(validateReviewReply("a".repeat(1000))).toBeNull();
    expect(validateReviewReply("a".repeat(1001))).toBe("MSG-57");
  });
  it("cắt khoảng trắng trước khi đếm", () => {
    expect(validateReviewReply(` ${"a".repeat(1000)} `)).toBeNull();
  });
});
