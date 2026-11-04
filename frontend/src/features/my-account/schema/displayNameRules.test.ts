import { describe, expect, it } from "vitest";
import { displayNameLength, trimDisplayName } from "./displayNameRules";
describe("tên hiển thị KAN-29", () => {
  it("trim giống Java, giữ whitespace nội bộ và Unicode whitespace ngoài phạm vi Java trim", () => {
    expect(trimDisplayName("\t Tên  có\nkhoảng trắng \r\n")).toBe(
      "Tên  có\nkhoảng trắng",
    );
    expect(trimDisplayName("\u00a0Tên\u00a0")).toBe("\u00a0Tên\u00a0");
  });
  it("đếm code points, giữ dấu tổ hợp, không tự chuẩn hóa nội dung", () => {
    expect(displayNameLength("😀😀")).toBe(2);
    expect(displayNameLength("😀".repeat(100))).toBe(100);
    expect(displayNameLength("a\u0301")).toBe(2);
    expect(trimDisplayName(" a\u0301 ")).toBe("a\u0301");
  });
});
