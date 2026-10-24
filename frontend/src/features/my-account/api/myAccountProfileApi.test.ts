import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { ApiError, isApiError, toFieldErrorMap } from "@/lib/api/apiError";
import {
  clearMockSignedInKind,
  setMockSignedInKind,
} from "@/lib/session/mockSignedInKind";
import { getMyAccount, resetMyAccountMockStore } from "./myAccountApi";
import {
  MOCK_EMAIL_CHANGE_OTP,
  MOCK_MY_ACCOUNT,
  MOCK_PATIENT_ACCOUNT,
  MOCK_TAKEN_EMAIL,
} from "./myAccountMockData";
import {
  requestMyEmailChange,
  updateMyDisplayName,
  verifyMyEmailChange,
} from "./myAccountProfileApi";

async function failure(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (err) {
    if (isApiError(err)) return err;
  }
  throw new Error("Mong đợi ApiError");
}

beforeEach(() => {
  setMockSignedInKind("PATIENT");
  resetMyAccountMockStore(MOCK_PATIENT_ACCOUNT);
});

afterEach(() => clearMockSignedInKind());

describe("myAccountProfileApi: tài khoản bệnh nhân", () => {
  it("GET /me trả biến thể PATIENT: có email, không có tên đăng nhập, đủ quyền tự sửa", async () => {
    const account = await getMyAccount();
    expect(account).toMatchObject({
      kind: "PATIENT",
      displayName: "Nguyễn Văn An",
      email: "an@example.com",
    });
    expect(account).not.toHaveProperty("username");
    expect(account.allowedActions).toEqual(
      expect.arrayContaining([
        "updateDisplayName",
        "changeEmail",
        "changePassword",
      ]),
    );
  });

  it("đổi tên hiển thị: lưu sau khi cắt khoảng trắng đầu cuối", async () => {
    const updated = await updateMyDisplayName("  Nguyễn Thị An  ");
    expect(updated.displayName).toBe("Nguyễn Thị An");
    expect(await getMyAccount()).toMatchObject({
      displayName: "Nguyễn Thị An",
    });
  });

  it("tên quá ngắn hoặc quá dài: 400 MSG-12 gắn vào displayName", async () => {
    const short = await failure(updateMyDisplayName(" A "));
    expect(short.status).toBe(400);
    expect(toFieldErrorMap(short.fieldErrors)).toEqual({ displayName: "MSG-12" });

    const long = await failure(updateMyDisplayName("A".repeat(101)));
    expect(toFieldErrorMap(long.fieldErrors)).toEqual({ displayName: "MSG-12" });
  });

  it("đổi email: email cũ vẫn hiệu lực tới khi nhập đúng mã (BR-05)", async () => {
    await requestMyEmailChange("moi@example.com");
    expect(await getMyAccount()).toMatchObject({ email: "an@example.com" });

    const verified = await verifyMyEmailChange(MOCK_EMAIL_CHANGE_OTP);
    expect(verified).toMatchObject({ email: "moi@example.com" });
    expect(await getMyAccount()).toMatchObject({ email: "moi@example.com" });
  });

  it("mã OTP sai: 400 MSG-08 gắn vào otp, email không đổi", async () => {
    await requestMyEmailChange("moi@example.com");
    const err = await failure(verifyMyEmailChange("000000"));
    expect(err.status).toBe(400);
    expect(toFieldErrorMap(err.fieldErrors)).toEqual({ otp: "MSG-08" });
    expect(await getMyAccount()).toMatchObject({ email: "an@example.com" });
  });

  it("xác minh khi chưa có yêu cầu đổi email: 400 MSG-08", async () => {
    const err = await failure(verifyMyEmailChange(MOCK_EMAIL_CHANGE_OTP));
    expect([err.status, err.code]).toEqual([400, "MSG-08"]);
  });

  it("email đã thuộc tài khoản khác hoặc trùng email hiện tại: 400 MSG-08 gắn vào newEmail", async () => {
    const taken = await failure(requestMyEmailChange(MOCK_TAKEN_EMAIL));
    expect(toFieldErrorMap(taken.fieldErrors)).toEqual({ newEmail: "MSG-08" });

    const same = await failure(requestMyEmailChange("an@example.com"));
    expect(toFieldErrorMap(same.fieldErrors)).toEqual({ newEmail: "MSG-08" });
  });

  it("email sai định dạng: 400 MSG-12 gắn vào newEmail", async () => {
    const err = await failure(requestMyEmailChange("khong-phai-email"));
    expect(toFieldErrorMap(err.fieldErrors)).toEqual({ newEmail: "MSG-12" });
  });
});

describe("myAccountProfileApi: tài khoản nhân viên", () => {
  it("không sửa được tên hiển thị hay email của nhân viên: 403 MSG-14", async () => {
    setMockSignedInKind("STAFF");
    resetMyAccountMockStore(MOCK_MY_ACCOUNT);

    const name = await failure(updateMyDisplayName("Tên mới"));
    expect([name.status, name.code]).toEqual([403, "MSG-14"]);

    const email = await failure(requestMyEmailChange("moi@example.com"));
    expect([email.status, email.code]).toEqual([403, "MSG-14"]);
  });
});
