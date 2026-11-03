import { beforeEach, describe, expect, it, vi } from "vitest";
import { isApiError } from "@/lib/api/apiError";
import { apiRequest } from "@/lib/api/client";
import {
  NON_EXISTENT_ACCOUNT_ID,
  resetFakeAccountBackend,
} from "@/test/accountAdminFakeBackend";
import {
  activateAccount,
  createAccount,
  deactivateAccount,
  getAccount,
  listAccounts,
  reissueAccountPassword,
} from "./accountApi";

// Mock ở ranh giới HTTP: backend giả trả đúng JSON của BE (KAN-27 cho nhân viên).
vi.mock("@/lib/api/client", async (importOriginal) => {
  const fake = await import("@/test/accountAdminFakeBackend");
  return {
    ...(await importOriginal<typeof import("@/lib/api/client")>()),
    apiRequest: vi.fn(fake.fakeAccountApiRequest),
  };
});

const paths = () => vi.mocked(apiRequest).mock.calls.map(([path]) => path);

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
  role: "ORTHOPEDIC_DOCTOR" as const,
  username: "bacsi.moi",
  initialPassword: "Xk7mQ2pL9vRt",
};

beforeEach(() => {
  resetFakeAccountBackend();
  vi.mocked(apiRequest).mockClear();
});

describe("listAccounts", () => {
  it("bệnh nhân là mặc định: gọi /admin/accounts, phân trang 20 dòng", async () => {
    const first = await listAccounts({ page: 0 });
    expect(paths()[0]).toMatch(/^\/api\/v1\/admin\/accounts\?/);
    expect(first.items).toHaveLength(20);
    expect(first.totalPages).toBe(3);
    expect(first.items.every((a) => a.accountType === "PATIENT")).toBe(true);
    const last = await listAccounts({ page: 2 });
    expect(last.items).toHaveLength(5);
  });

  it("nhân viên: gọi /admin/staff-accounts và chuyển dòng KAN-27 sang dạng FE", async () => {
    const result = await listAccounts({ scope: "STAFF" });
    expect(paths()[0]).toMatch(/^\/api\/v1\/admin\/staff-accounts\?/);
    const an = result.items.find((a) => a.username === "letan.an")!;
    expect(an).toEqual({
      id: "00000000-0000-4000-8000-000000000001",
      accountType: "RECEPTIONIST",
      username: "letan.an",
      email: null,
      fullName: "Nguyễn Thị An",
      status: "ACTIVE",
      createdAt: expect.any(String),
      allowedActions: ["deactivate", "reissuePassword"],
    });
    // Danh sách không mang hồ sơ, không lộ trường riêng của BE.
    expect(an).not.toHaveProperty("staffProfile");
    expect(an).not.toHaveProperty("accountId");
    const noProfile = result.items.find((a) => a.username === "kttd.cuong")!;
    expect(noProfile.fullName).toBeNull();
    expect(noProfile.allowedActions).toEqual([
      "activate",
      "reissuePassword",
      "createStaffProfile",
    ]);
  });

  it("nhân viên: lọc theo vai trò, trạng thái và từ khóa gửi đúng tham số", async () => {
    const doctors = await listAccounts({
      scope: "STAFF",
      role: "ORTHOPEDIC_DOCTOR",
    });
    expect(doctors.items.map((a) => a.username)).toEqual(["trung"]);
    expect(paths()[0]).toContain("role=ORTHOPEDIC_DOCTOR");
    const inactive = await listAccounts({ scope: "STAFF", status: "INACTIVE" });
    expect(inactive.items.map((a) => a.username)).toEqual(["kttd.cuong"]);
    const byName = await listAccounts({ scope: "STAFF", q: "Phạm" });
    expect(byName.items.map((a) => a.username)).toEqual(["ktxn.dung"]);
  });

  it("bệnh nhân không gửi tham số role", async () => {
    await listAccounts({ scope: "PATIENT", role: "RECEPTIONIST" });
    expect(paths()[0]).not.toContain("role=");
  });

  it("từ khóa dài hơn 100 ký tự -> 400 MSG-54", async () => {
    await expectApiError(listAccounts({ q: "a".repeat(101) }), 400, "MSG-54");
  });
});

describe("getAccount", () => {
  it("nhân viên: chi tiết có hồ sơ chuyển từ displayName", async () => {
    const id = "00000000-0000-4000-8000-000000000002";
    const detail = await getAccount(id, "STAFF");
    expect(paths()[0]).toBe(`/api/v1/admin/staff-accounts/${id}`);
    expect(detail).toMatchObject({
      id,
      accountType: "ORTHOPEDIC_DOCTOR",
      username: "trung",
      email: null,
      fullName: "Trần Văn Trung",
      staffProfile: {
        fullName: "Trần Văn Trung",
        role: "ORTHOPEDIC_DOCTOR",
        workContact: null,
      },
    });
  });

  it("nhân viên chưa có hồ sơ: staffProfile null và có Tạo hồ sơ", async () => {
    const detail = await getAccount("00000000-0000-4000-8000-000000000003", "STAFF");
    expect(detail.staffProfile).toBeNull();
    expect(detail.allowedActions).toContain("createStaffProfile");
  });

  it("bệnh nhân: gọi /admin/accounts và giữ nguyên dữ liệu", async () => {
    const [first] = (await listAccounts({ scope: "PATIENT" })).items;
    const detail = await getAccount(first.id, "PATIENT");
    expect(paths().at(-1)).toBe(`/api/v1/admin/accounts/${first.id}`);
    expect(detail.email).toBe(first.email);
  });

  it("id không có -> 404 MSG-14", async () => {
    await expectApiError(getAccount(NON_EXISTENT_ACCOUNT_ID, "STAFF"), 404, "MSG-14");
  });
});

describe("createAccount", () => {
  it("gửi đúng {role, username, initialPassword}, giữ nguyên vai trò bác sĩ", async () => {
    await createAccount(valid);
    const [path, init] = vi.mocked(apiRequest).mock.calls[0];
    expect(path).toBe("/api/v1/admin/staff-accounts");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(init!.body as string)).toEqual(valid);
  });

  it("trả id, tên đăng nhập BE và mật khẩu vừa nhập; GET sau đó không có mật khẩu", async () => {
    const issued = await createAccount(valid);
    expect(issued).toEqual({
      id: expect.any(String),
      username: "bacsi.moi",
      initialPassword: valid.initialPassword,
    });
    const detail = await getAccount(issued.id, "STAFF");
    expect(JSON.stringify(detail)).not.toContain(valid.initialPassword);
    const list = await listAccounts({ scope: "STAFF", q: "bacsi.moi" });
    expect(list.items).toHaveLength(1);
    expect(JSON.stringify(list)).not.toContain(valid.initialPassword);
  });

  it("409 MSG-13 khi trùng tên đăng nhập, kèm fieldErrors", async () => {
    const error = await expectApiError(
      createAccount({ ...valid, username: "trung" }),
      409,
      "MSG-13",
    );
    expect(error.fieldErrors).toEqual([{ field: "username", code: "MSG-13" }]);
  });

  it("403 MSG-14 và 500 MSG-16 giữ nguyên mã của BE", async () => {
    await expectApiError(
      createAccount({ ...valid, username: "khong.quyen" }),
      403,
      "MSG-14",
    );
    await expectApiError(
      createAccount({ ...valid, username: "loi.tao" }),
      500,
      "MSG-16",
    );
  });
});

describe("đổi trạng thái", () => {
  it("nhân viên: vô hiệu hóa rồi kích hoạt qua staff-accounts, allowedActions đổi theo", async () => {
    const id = "00000000-0000-4000-8000-000000000001";
    const off = await deactivateAccount(id, "STAFF");
    expect(paths()[0]).toBe(`/api/v1/admin/staff-accounts/${id}/deactivation`);
    expect(off.status).toBe("INACTIVE");
    expect(off.allowedActions).toContain("activate");
    expect(off.allowedActions).not.toContain("deactivate");
    const on = await activateAccount(id, "STAFF");
    expect(on.status).toBe("ACTIVE");
    expect(on.allowedActions).toContain("deactivate");
  });

  it("bệnh nhân: đổi trạng thái qua /admin/accounts", async () => {
    const [first] = (await listAccounts({ scope: "PATIENT", status: "ACTIVE" }))
      .items;
    const off = await deactivateAccount(first.id, "PATIENT");
    expect(paths().at(-1)).toBe(`/api/v1/admin/accounts/${first.id}/deactivation`);
    expect(off.status).toBe("INACTIVE");
    expect(off.allowedActions).toEqual(["activate"]);
  });

  it("đã ở trạng thái đích -> 409 MSG-42; id không có -> 404", async () => {
    await expectApiError(
      activateAccount("00000000-0000-4000-8000-000000000001", "STAFF"),
      409,
      "MSG-42",
    );
    await expectApiError(
      deactivateAccount("00000000-0000-4000-8000-000000000003", "STAFF"),
      409,
      "MSG-42",
    );
    await expectApiError(
      deactivateAccount(NON_EXISTENT_ACCOUNT_ID, "STAFF"),
      404,
      "MSG-14",
    );
  });
});

describe("reissueAccountPassword", () => {
  it("đọc temporaryPassword của BE, mỗi lần một mật khẩu khác", async () => {
    const id = "00000000-0000-4000-8000-000000000001";
    const a = await reissueAccountPassword(id, "letan.an");
    const b = await reissueAccountPassword(id, "letan.an");
    expect(paths()[0]).toBe(`/api/v1/admin/staff-accounts/${id}/password-reissues`);
    expect(a).toMatchObject({ id, username: "letan.an" });
    expect(a.initialPassword).toBeTruthy();
    expect(a.initialPassword).not.toBe(b.initialPassword);
  });
});
