import { beforeEach, describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import {
  createStaffProfile,
  getStaffProfile,
  listEligibleStaffAccounts,
  listStaffProfiles,
  removeStaffProfile,
  updateStaffProfile,
} from "./staffProfileApi";
import { resetStaffProfileMockStore } from "./staffProfileMockData";
import type { StaffProfileFilters } from "../types/staffProfile";

const ALL: StaffProfileFilters = { q: "", role: "", status: "", isPublic: "" };

async function findId(username: string) {
  const page = await listStaffProfiles({ ...ALL, size: 100 });
  const item = page.items.find((p) => p.username === username);
  if (!item) throw new Error(`Thiếu hồ sơ ${username} trong dữ liệu giả`);
  return item.id;
}

async function expectApiError(promise: Promise<unknown>, status: number, code: string) {
  const error = await promise.then(
    () => null,
    (e: unknown) => e,
  );
  expect(error).toBeInstanceOf(ApiError);
  expect((error as ApiError).status).toBe(status);
  expect((error as ApiError).code).toBe(code);
  return error as ApiError;
}

beforeEach(() => resetStaffProfileMockStore());

describe("listStaffProfiles", () => {
  it("45 hồ sơ chia 3 trang với size 20", async () => {
    const first = await listStaffProfiles({ ...ALL, page: 0, size: 20 });
    expect(first.totalItems).toBe(45);
    expect(first.totalPages).toBe(3);
    expect(first.items).toHaveLength(20);
    const last = await listStaffProfiles({ ...ALL, page: 2, size: 20 });
    expect(last.items).toHaveLength(5);
  });

  it("lọc theo từ khóa (họ tên hoặc tên đăng nhập) và q=zzz trả rỗng", async () => {
    const byName = await listStaffProfiles({ ...ALL, q: "TRẦN THU HÀ" });
    expect(byName.items.map((p) => p.username)).toEqual(["bs.ha"]);
    const byUser = await listStaffProfiles({ ...ALL, q: "le.tan.hoa" });
    expect(byUser.items).toHaveLength(1);
    expect((await listStaffProfiles({ ...ALL, q: "zzz" })).totalItems).toBe(0);
  });

  it("lọc theo vai trò và trạng thái hồ sơ", async () => {
    const doctors = await listStaffProfiles({ ...ALL, role: "ORTHOPEDIC_DOCTOR" });
    expect(doctors.totalItems).toBe(6);
    const removed = await listStaffProfiles({ ...ALL, status: "REMOVED" });
    expect(removed.items.every((p) => p.status === "REMOVED")).toBe(true);
    expect(removed.items.every((p) => p.allowedActions.length === 0)).toBe(true);
  });

  it("bộ lọc công khai chỉ khớp bác sĩ; nhân viên khác có isPublic null", async () => {
    const yes = await listStaffProfiles({ ...ALL, isPublic: "true" });
    expect(yes.items.map((p) => p.username).sort()).toEqual(["bs.ha", "bs.minh", "bs.trung"]);
    const no = await listStaffProfiles({ ...ALL, isPublic: "false" });
    expect(no.items.every((p) => p.role === "ORTHOPEDIC_DOCTOR")).toBe(true);
    const receptionist = await listStaffProfiles({ ...ALL, q: "le.tan.hoa" });
    expect(receptionist.items[0].isPublic).toBeNull();
  });
});

describe("tài khoản đủ điều kiện", () => {
  it("không gồm tài khoản đã có hồ sơ và loại tài khoản vừa được tạo hồ sơ", async () => {
    const before = await listEligibleStaffAccounts();
    expect(before.map((a) => a.username)).not.toContain("le.tan.hoa");
    const account = before.find((a) => a.username === "le.tan.minh")!;
    await createStaffProfile({
      accountId: account.accountId,
      displayName: "Lê Minh Tân",
      workContact: "0912 345 678",
    });
    const after = await listEligibleStaffAccounts();
    expect(after).toHaveLength(before.length - 1);
    expect(after.map((a) => a.accountId)).not.toContain(account.accountId);
  });
});

describe("createStaffProfile", () => {
  it("tài khoản đã có hồ sơ (le.tan.hoa) trả 409 MSG-23", async () => {
    const profile = await getStaffProfile(await findId("le.tan.hoa"));
    await expectApiError(
      createStaffProfile({
        accountId: profile.accountId,
        displayName: "Trần Thị Hoa",
        workContact: "0901 000 000",
      }),
      409,
      "MSG-23",
    );
  });

  it("thiếu họ tên hoặc liên hệ trả 400 MSG-22 kèm fieldErrors MSG-12", async () => {
    const account = (await listEligibleStaffAccounts()).find((a) => a.username === "le.tan.lan")!;
    const error = await expectApiError(
      createStaffProfile({ accountId: account.accountId, displayName: " ", workContact: "" }),
      400,
      "MSG-22",
    );
    expect(error.fieldErrors).toEqual([
      { field: "displayName", code: "MSG-12" },
      { field: "workContact", code: "MSG-12" },
    ]);
  });

  it("tài khoản kt.loi gặp lỗi dịch vụ MSG-04", async () => {
    const account = (await listEligibleStaffAccounts()).find((a) => a.username === "kt.loi")!;
    await expectApiError(
      createStaffProfile({ accountId: account.accountId, displayName: "Đặng Văn Lợi", workContact: "0977 111 222" }),
      500,
      "MSG-04",
    );
  });

  it("bác sĩ tạo với công khai tắt không lên danh sách trang chủ", async () => {
    const account = (await listEligibleStaffAccounts()).find((a) => a.username === "bs.hung")!;
    const created = await createStaffProfile({
      accountId: account.accountId,
      displayName: "BS. Bùi Quốc Hùng",
      workContact: "hung@example.com",
      doctor: { specialties: ["Cột sống"], qualifications: "", biography: "", consultationInfo: "", isPublic: false },
    });
    expect(created.doctor?.isPublic).toBe(false);
    expect(created.publicUrl).toBeNull();
  });
});

describe("updateStaffProfile", () => {
  it("sửa thiếu tên trả MSG-22 kèm fieldErrors", async () => {
    const id = await findId("le.tan.hoa");
    const error = await expectApiError(
      updateStaffProfile(id, { displayName: "", workContact: "0901 000 000" }),
      400,
      "MSG-22",
    );
    expect(error.fieldErrors).toEqual([{ field: "displayName", code: "MSG-12" }]);
  });

  it("hồ sơ kt.loi.xn gặp lỗi dịch vụ MSG-04", async () => {
    const id = await findId("kt.loi.xn");
    await expectApiError(
      updateStaffProfile(id, { displayName: "KTV XN Đặng Lợi", workContact: "0977 000 111" }),
      500,
      "MSG-04",
    );
  });

  it("bật công khai cập nhật publicUrl trỏ tới trang bác sĩ công khai", async () => {
    const id = await findId("bs.yen");
    const detail = await getStaffProfile(id);
    const updated = await updateStaffProfile(id, {
      displayName: detail.displayName,
      workContact: detail.workContact,
      doctor: { ...detail.doctor!, isPublic: true },
    });
    expect(updated.publicUrl).toBe(`/doctors/${id}`);
  });
});

describe("removeStaffProfile", () => {
  it("bs.trung còn lịch hẹn tương lai: 409 MSG-36 và hồ sơ vẫn Active", async () => {
    const id = await findId("bs.trung");
    await expectApiError(removeStaffProfile(id), 409, "MSG-36");
    expect((await getStaffProfile(id)).status).toBe("ACTIVE");
  });

  it("bs.minh gỡ được và chuyển REMOVED", async () => {
    const id = await findId("bs.minh");
    await expect(removeStaffProfile(id)).resolves.toBeUndefined();
    const detail = await getStaffProfile(id);
    expect(detail.status).toBe("REMOVED");
    expect(detail.allowedActions).toEqual([]);
  });

  it("hồ sơ không tồn tại trả 404 MSG-23", async () => {
    await expectApiError(getStaffProfile("khong-co"), 404, "MSG-23");
  });
});
