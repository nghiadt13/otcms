import { describe, expect, it } from "vitest";
import { isApiError, toFieldErrorMap } from "@/lib/api/apiError";
import { FirstLoginPasswordError } from "@/lib/auth/changePasswordApi";
import { toPasswordApiError } from "./myAccountApi";

function convert(
  reason: ConstructorParameters<typeof FirstLoginPasswordError>[0],
) {
  const error = toPasswordApiError(new FirstLoginPasswordError(reason));
  if (!isApiError(error)) throw new Error("Mong đợi ApiError");
  return error;
}

describe("toPasswordApiError", () => {
  it("mật khẩu hiện tại sai: 400 gắn vào currentPassword", () => {
    const error = convert("current");
    expect(error.status).toBe(400);
    expect(toFieldErrorMap(error.fieldErrors)).toEqual({
      currentPassword: "MSG-08",
    });
  });

  it("mật khẩu mới sai chính sách hoặc trùng cũ: 400 gắn vào newPassword", () => {
    for (const reason of ["invalid", "reused"] as const) {
      expect(toFieldErrorMap(convert(reason).fieldErrors)).toEqual({
        newPassword: "MSG-08",
      });
    }
  });

  it("hết phiên: 401 MSG-14; lỗi dịch vụ: 500 MSG-04", () => {
    expect([convert("session").status, convert("session").code]).toEqual([
      401,
      "MSG-14",
    ]);
    expect([convert("service").status, convert("service").code]).toEqual([
      500,
      "MSG-04",
    ]);
  });

  it("lỗi khác được ném lại nguyên vẹn", () => {
    const other = new Error("mạng");
    expect(toPasswordApiError(other)).toBe(other);
  });
});
