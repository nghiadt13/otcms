import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { ApiError, isApiError, toFieldErrorMap } from "@/lib/api/apiError";
import {
  clearMockSignedInKind,
  setMockSignedInKind,
} from "@/lib/session/mockSignedInKind";
import { getMyAccount, resetMyAccountMockStore } from "./myAccountApi";
import { MOCK_MY_ACCOUNT, MOCK_PATIENT_ACCOUNT } from "./myAccountMockData";
import { updateMyDisplayName } from "./myAccountProfileApi";

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
      expect.arrayContaining(["updateDisplayName", "changePassword"]),
    );
    expect(account.allowedActions).not.toContain("changeEmail");
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
    expect(toFieldErrorMap(short.fieldErrors)).toEqual({
      displayName: "MSG-12",
    });

    const long = await failure(updateMyDisplayName("A".repeat(101)));
    expect(toFieldErrorMap(long.fieldErrors)).toEqual({
      displayName: "MSG-12",
    });
  });
});

describe("myAccountProfileApi: tài khoản nhân viên", () => {
  it("không sửa được tên hiển thị của nhân viên: 403 MSG-14", async () => {
    setMockSignedInKind("STAFF");
    resetMyAccountMockStore(MOCK_MY_ACCOUNT);

    const name = await failure(updateMyDisplayName("Tên mới"));
    expect([name.status, name.code]).toEqual([403, "MSG-14"]);
  });
});
