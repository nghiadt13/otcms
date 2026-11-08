import { beforeEach, describe, expect, it } from "vitest";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import { postSignInTarget } from "./postSignInTarget";

// UC01 AF: đăng nhập xong, tài khoản chưa có hồ sơ Bản thân phải điền Thông tin cá nhân trước.
describe("postSignInTarget", () => {
  beforeEach(() => resetLinkedProfilesMock());

  it("đã có hồ sơ Bản thân: giữ đích cũ", async () => {
    expect(await postSignInTarget("/portal/appointments")).toBe(
      "/portal/appointments",
    );
    expect(await postSignInTarget("/")).toBe("/");
  });

  it("chưa có hồ sơ: tới Thông tin cá nhân, giữ đích cũ làm next", async () => {
    resetLinkedProfilesMock({ variant: "empty" });
    expect(await postSignInTarget("/portal/appointments/new")).toBe(
      "/portal/account?next=%2Fportal%2Fappointments%2Fnew",
    );
  });

  it("chưa có hồ sơ và đích là trang chủ: không gắn next", async () => {
    resetLinkedProfilesMock({ variant: "empty" });
    expect(await postSignInTarget("/")).toBe("/portal/account");
  });

  it("không đọc được danh sách hồ sơ: không làm kẹt đăng nhập", async () => {
    resetLinkedProfilesMock({ failList: true });
    expect(await postSignInTarget("/portal/results")).toBe("/portal/results");
  });
});
