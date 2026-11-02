import { describe, expect, it } from "vitest";
import { validateReviewRating, validateReviewText } from "./myReviewFieldRules";

describe("validateReviewText", () => {
  it("10 đến 1000 ký tự sau khi cắt khoảng trắng là hợp lệ", () => {
    expect(validateReviewText("a".repeat(10))).toBeNull();
    expect(validateReviewText("a".repeat(1000))).toBeNull();
    expect(validateReviewText(`   ${"a".repeat(10)}   `)).toBeNull();
  });

  it("dưới 10 ký tự (kể cả chỉ có khoảng trắng) hoặc trên 1000 ký tự: MSG-57", () => {
    expect(validateReviewText("")).toBe("MSG-57");
    expect(validateReviewText("   ")).toBe("MSG-57");
    expect(validateReviewText(`   ${"a".repeat(9)}   `)).toBe("MSG-57");
    expect(validateReviewText("a".repeat(1001))).toBe("MSG-57");
  });
});

describe("validateReviewRating", () => {
  it("số nguyên từ 1 đến 5 là hợp lệ", () => {
    for (const rating of [1, 2, 3, 4, 5]) {
      expect(validateReviewRating(rating)).toBeNull();
    }
  });

  it("chưa chọn, ngoài khoảng, hoặc không nguyên: MSG-57", () => {
    for (const rating of [null, 0, 6, 2.5, Number.NaN]) {
      expect(validateReviewRating(rating)).toBe("MSG-57");
    }
  });
});
