import { afterEach, describe, expect, it, vi } from "vitest";
import type { CreateStaffInput } from "../types/account";

const newStaff: CreateStaffInput = {
  fullName: "BS. Trần Văn Hùng",
  email: "hung.tv@otcms.vn",
  phone: "0911223344",
  role: "orthopedic_doctor",
};

async function loadApi() {
  vi.resetModules();
  vi.useFakeTimers();
  return import("./accountApi");
}

async function settled<T>(promise: Promise<T>): Promise<T> {
  const outcome = Promise.allSettled([promise]);
  await vi.runAllTimersAsync();
  const [result] = await outcome;
  if (result.status === "rejected") throw result.reason;
  return result.value;
}

describe("accountApi (API giả)", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("liệt kê dữ liệu tài khoản mẫu", async () => {
    const api = await loadApi();
    const list = await settled(api.listAccounts());
    expect(list.length).toBeGreaterThan(0);
    expect(list.some((acc) => acc.role === "orthopedic_doctor")).toBe(true);
  });

  it("tạo tài khoản nhân viên mới ở trạng thái chờ kích hoạt (BR-16)", async () => {
    const api = await loadApi();
    const created = await settled(api.createStaffAccount(newStaff));

    expect(created.fullName).toBe("BS. Trần Văn Hùng");
    expect(created.email).toBe("hung.tv@otcms.vn");
    expect(created.status).toBe("pending_invitation");
    expect(created.role).toBe("orthopedic_doctor");
  });

  it("từ chối email đã tồn tại với thông báo MSG-13", async () => {
    const api = await loadApi();

    await expect(
      settled(
        api.createStaffAccount({
          ...newStaff,
          email: "LONG.NV@OTCMS.VN", // trùng hoa thường với user mẫu
        }),
      ),
    ).rejects.toThrow(
      "Email này không thể dùng để tạo tài khoản mới. Vui lòng chọn email khác.",
    );
  });

  it("cập nhật trạng thái tài khoản thành công", async () => {
    const api = await loadApi();
    const updated = await settled(
      api.updateAccountStatus("user-01", "inactive"),
    );
    expect(updated.status).toBe("inactive");
  });
});
