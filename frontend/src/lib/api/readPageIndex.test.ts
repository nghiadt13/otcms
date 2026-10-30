import { describe, expect, it } from "vitest";
import { readPageIndex } from "./readPageIndex";

describe("readPageIndex", () => {
  it("đổi số trang đánh từ 1 sang chỉ số 0-based", () => {
    expect(readPageIndex("1")).toBe(0);
    expect(readPageIndex("3")).toBe(2);
  });

  it("thiếu hoặc sai định dạng thì về trang đầu", () => {
    expect(readPageIndex(undefined)).toBe(0);
    expect(readPageIndex("0")).toBe(0);
    expect(readPageIndex("-2")).toBe(0);
    expect(readPageIndex("abc")).toBe(0);
    expect(readPageIndex(["2", "3"])).toBe(0);
  });
});
