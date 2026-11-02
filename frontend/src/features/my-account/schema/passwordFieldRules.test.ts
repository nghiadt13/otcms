import { describe, expect, it } from "vitest";
import {
  passwordMessages,
  validateConfirmPassword,
  validateCurrentPassword,
  validateNewPassword,
} from "./passwordFieldRules";

describe("passwordFieldRules", () => {
  it("bắt buộc mật khẩu hiện tại", () => {
    expect(validateCurrentPassword("")).toBe(passwordMessages.currentRequired);
    expect(validateCurrentPassword("abc")).toBeNull();
  });

  it.each(["abc1234", "abcdefgh", "12345678"])(
    "từ chối mật khẩu mới không đạt: %s",
    (value) => {
      expect(validateNewPassword(value)).toBe(passwordMessages.newInvalid);
    },
  );

  it("chấp nhận mật khẩu mới có chữ, số, đủ 8 ký tự", () => {
    expect(validateNewPassword("MatKhauMoi2")).toBeNull();
    expect(validateNewPassword("")).toBe(passwordMessages.newRequired);
  });

  it("nhập lại phải trùng và không được để trống", () => {
    expect(validateConfirmPassword("MatKhauMoi2", "")).toBe(
      passwordMessages.confirmRequired,
    );
    expect(validateConfirmPassword("MatKhauMoi2", "KhacHan3")).toBe(
      passwordMessages.confirmMismatch,
    );
    expect(validateConfirmPassword("MatKhauMoi2", "MatKhauMoi2")).toBeNull();
  });
});
