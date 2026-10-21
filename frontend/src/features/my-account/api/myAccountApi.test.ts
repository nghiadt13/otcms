import { beforeEach, describe, expect, it } from "vitest";
import { isApiError, toFieldErrorMap } from "@/lib/api/apiError";
import {
  changeMyPassword,
  getMyAccount,
  removeMyAvatar,
  resetMyAccountMockStore,
  updateMyAvatar,
} from "./myAccountApi";
import { MOCK_MY_ACCOUNT_FIRST_LOGIN } from "./myAccountMockData";

async function failure(promise: Promise<unknown>) {
  try {
    await promise;
  } catch (err) {
    if (isApiError(err)) return err;
  }
  throw new Error("Mong đợi ApiError");
}

beforeEach(() => resetMyAccountMockStore());

describe("myAccountApi", () => {
  it("getMyAccount: Admin không có email, chưa có ảnh thì không có removeAvatar", async () => {
    const account = await getMyAccount();
    expect(account.username).toBe("admin.hoa");
    expect(account).not.toHaveProperty("email");
    expect(account.allowedActions).toEqual(["updateAvatar", "changePassword"]);
  });

  it("đổi ảnh hợp lệ thì có removeAvatar; gỡ ảnh trả undefined", async () => {
    const file = new File(["x"], "a.png", { type: "image/png" });
    const updated = await updateMyAvatar(file);
    expect(updated.avatarUrl).toBeTruthy();
    expect(updated.allowedActions).toContain("removeAvatar");
    await expect(removeMyAvatar()).resolves.toBeUndefined();
    expect((await getMyAccount()).avatarUrl).toBeNull();
  });

  it("ảnh sai định dạng hoặc quá cỡ: fieldErrors ở file", async () => {
    const gif = new File(["x"], "a.gif", { type: "image/gif" });
    const err = await failure(updateMyAvatar(gif));
    expect(err.status).toBe(400);
    expect(toFieldErrorMap(err.fieldErrors)).toEqual({ file: "MSG-08" });

    const big = new File([new Uint8Array(2 * 1024 * 1024 + 1)], "b.png", {
      type: "image/png",
    });
    const bigErr = await failure(updateMyAvatar(big));
    expect(toFieldErrorMap(bigErr.fieldErrors)).toEqual({ file: "MSG-08" });
  });

  it("mật khẩu hiện tại sai: fieldErrors ở currentPassword", async () => {
    const err = await failure(
      changeMyPassword({
        currentPassword: "SaiMatKhau1",
        newPassword: "MatKhauMoi2",
      }),
    );
    expect(toFieldErrorMap(err.fieldErrors)).toEqual({
      currentPassword: "MSG-08",
    });
  });

  it("mật khẩu mới không đạt chính sách: fieldErrors ở newPassword", async () => {
    const err = await failure(
      changeMyPassword({
        currentPassword: "MatKhauCu1",
        newPassword: "abcdefgh",
      }),
    );
    expect(toFieldErrorMap(err.fieldErrors)).toEqual({ newPassword: "MSG-08" });
  });

  it("LoiDichVu1 trả 500 MSG-04", async () => {
    const err = await failure(
      changeMyPassword({
        currentPassword: "LoiDichVu1",
        newPassword: "MatKhauMoi2",
      }),
    );
    expect([err.status, err.code]).toEqual([500, "MSG-04"]);
  });

  it("đổi thành công thì tắt mustChangePassword", async () => {
    resetMyAccountMockStore(MOCK_MY_ACCOUNT_FIRST_LOGIN);
    expect((await getMyAccount()).mustChangePassword).toBe(true);
    await expect(
      changeMyPassword({
        currentPassword: "MatKhauCu1",
        newPassword: "MatKhauMoi2",
      }),
    ).resolves.toBeUndefined();
    expect((await getMyAccount()).mustChangePassword).toBe(false);
  });
});
