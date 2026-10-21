import { beforeEach, describe, expect, it } from "vitest";
import { isApiError } from "@/lib/api/apiError";
import { NON_EXISTENT_ACCOUNT_ID, resetAccountMockStore } from "./accountMockData";
import {
  activateAccount,
  createAccount,
  deactivateAccount,
  getAccount,
  listAccounts,
  reissueAccountPassword,
} from "./accountApi";

async function expectApiError(
  promise: Promise<unknown>,
  status: number,
  code: string,
) {
  const error = await promise.then(
    () => null,
    (e: unknown) => e,
  );
  expect(isApiError(error)).toBe(true);
  expect(error).toMatchObject({ status, code });
  return error as { fieldErrors?: { field: string; code: string }[] };
}

const valid = {
  role: "RECEPTIONIST" as const,
  username: "letan.moi",
  initialPassword: "Xk7mQ2pL9vRt",
};

beforeEach(() => resetAccountMockStore());

describe("listAccounts", () => {
  it("phân trang 20 dòng, nhiều trang", async () => {
    const first = await listAccounts({ page: 0 });
    expect(first.items).toHaveLength(20);
    expect(first.totalPages).toBeGreaterThanOrEqual(3);
    const last = await listAccounts({ page: first.totalPages - 1 });
    expect(last.items.length).toBeGreaterThan(0);
  });

  it("lọc theo vai trò, trạng thái và từ khóa", async () => {
    const patients = await listAccounts({ role: "PATIENT", size: 100 });
    expect(patients.items.every((a) => a.accountType === "PATIENT")).toBe(true);
    const inactive = await listAccounts({ status: "INACTIVE", size: 100 });
    expect(inactive.items.every((a) => a.status === "INACTIVE")).toBe(true);
    expect(inactive.items.length).toBeGreaterThan(0);
    const byName = await listAccounts({ q: "lan.tran@" });
    expect(byName.items.map((a) => a.email)).toEqual(["lan.tran@mail.vn"]);
    const none = await listAccounts({ q: "khong-co-ai" });
    expect(none.items).toEqual([]);
  });

  it("không trả hồ sơ nhân viên trong danh sách và không có Administrator", async () => {
    const all = await listAccounts({ size: 100 });
    expect(all.items.every((a) => !("staffProfile" in a))).toBe(true);
    expect(
      all.items.some((a) => (a.accountType as string) === "ADMIN"),
    ).toBe(false);
  });

  it("từ khóa dài hơn 100 ký tự -> 400 MSG-54", async () => {
    await expectApiError(listAccounts({ q: "a".repeat(101) }), 400, "MSG-54");
  });
});

describe("createAccount", () => {
  it("201 trả mật khẩu một lần; GET sau đó không có mật khẩu", async () => {
    const issued = await createAccount(valid);
    expect(issued).toMatchObject({ username: "letan.moi", initialPassword: valid.initialPassword });

    const detail = await getAccount(issued.id);
    expect(JSON.stringify(detail)).not.toContain(valid.initialPassword);
    expect(detail.allowedActions).toEqual([
      "deactivate",
      "reissuePassword",
      "createStaffProfile",
    ]);
    const list = await listAccounts({ q: "letan.moi" });
    expect(JSON.stringify(list)).not.toContain(valid.initialPassword);
    expect(list.items).toHaveLength(1);
  });

  it("409 MSG-13 khi trùng tên đăng nhập", async () => {
    const error = await expectApiError(
      createAccount({ ...valid, username: "trung" }),
      409,
      "MSG-13",
    );
    expect(error.fieldErrors).toEqual([{ field: "username", code: "MSG-13" }]);
  });

  it("400 MSG-12 kèm fieldErrors theo ô sai", async () => {
    const error = await expectApiError(
      createAccount({ ...valid, username: "Le An", initialPassword: "abc" }),
      400,
      "MSG-12",
    );
    expect(error.fieldErrors?.map((f) => f.field)).toEqual([
      "username",
      "initialPassword",
    ]);
  });

  it("403 MSG-14 và 500 MSG-16 theo tên đăng nhập thử", async () => {
    await expectApiError(createAccount({ ...valid, username: "khong.quyen" }), 403, "MSG-14");
    await expectApiError(createAccount({ ...valid, username: "loi.tao" }), 500, "MSG-16");
  });
});

describe("đổi trạng thái", () => {
  it("vô hiệu hóa rồi kích hoạt, allowedActions đổi theo", async () => {
    const [first] = (await listAccounts({ role: "PATIENT", status: "ACTIVE" })).items;
    const off = await deactivateAccount(first.id);
    expect(off.status).toBe("INACTIVE");
    expect(off.allowedActions).toEqual(["activate"]);
    const on = await activateAccount(first.id);
    expect(on.status).toBe("ACTIVE");
    expect(on.allowedActions).toEqual(["deactivate"]);
  });

  it("kích hoạt tài khoản đang Active -> 409 MSG-42", async () => {
    const [active] = (await listAccounts({ status: "ACTIVE" })).items;
    await expectApiError(activateAccount(active.id), 409, "MSG-42");
    const [inactive] = (await listAccounts({ status: "INACTIVE" })).items;
    await expectApiError(deactivateAccount(inactive.id), 409, "MSG-42");
  });

  it("id không có -> 404", async () => {
    await expectApiError(getAccount(NON_EXISTENT_ACCOUNT_ID), 404, "MSG-14");
    await expectApiError(deactivateAccount(NON_EXISTENT_ACCOUNT_ID), 404, "MSG-14");
  });
});

describe("reissueAccountPassword", () => {
  it("nhân viên nhận mật khẩu mới, mỗi lần một khác", async () => {
    const [staff] = (await listAccounts({ role: "RECEPTIONIST" })).items;
    const a = await reissueAccountPassword(staff.id);
    const b = await reissueAccountPassword(staff.id);
    expect(a.username).toBe(staff.username);
    expect(a.initialPassword).not.toBe(b.initialPassword);
  });

  it("bệnh nhân -> 403 MSG-14", async () => {
    const [patient] = (await listAccounts({ role: "PATIENT" })).items;
    expect(patient.allowedActions).not.toContain("reissuePassword");
    await expectApiError(reissueAccountPassword(patient.id), 403, "MSG-14");
  });
});
