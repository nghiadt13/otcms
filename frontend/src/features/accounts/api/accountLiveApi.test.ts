import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import {
  listLiveAccounts,
  getLiveAccount,
  changeLiveAccountStatus,
  createLiveStaffAccount,
} from "./accountLiveApi";

vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
beforeEach(() => vi.mocked(apiRequest).mockReset());

describe("Admin account live API", () => {
  it("tải tab bệnh nhân bằng endpoint hiện có và giữ phân trang server", async () => {
    const page = { items: [], page: 1, size: 20, totalItems: 0, totalPages: 0 };
    vi.mocked(apiRequest).mockResolvedValueOnce(page);
    expect(
      await listLiveAccounts({
        scope: "PATIENT",
        q: "An",
        status: "ACTIVE",
        page: 1,
      }),
    ).toEqual(page);
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/v1/admin/accounts?q=An&status=ACTIVE&page=1&size=20",
      expect.any(Object),
    );
  });

  it("tab nhân viên không trộn dữ liệu bệnh nhân khi backend chưa có endpoint", async () => {
    vi.mocked(apiRequest).mockRejectedValueOnce(new ApiError(404));
    await expect(listLiveAccounts({ scope: "STAFF" })).rejects.toThrow(
      "Backend chưa có API danh sách tài khoản nhân viên",
    );
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/v1/admin/staff-accounts?page=0&size=20",
      expect.any(Object),
    );
  });

  it("chi tiết và đổi trạng thái bệnh nhân gọi backend", async () => {
    vi.mocked(apiRequest).mockResolvedValue({ id: "patient-1" });
    await getLiveAccount("patient-1");
    await changeLiveAccountStatus("patient-1", "deactivation");
    expect(apiRequest).toHaveBeenNthCalledWith(
      1,
      "/api/v1/admin/accounts/patient-1",
    );
    expect(apiRequest).toHaveBeenNthCalledWith(
      2,
      "/api/v1/admin/accounts/patient-1/deactivation",
      { method: "POST" },
    );
  });

  it("tạo nhân viên gửi email, họ tên và vai trò backend", async () => {
    vi.mocked(apiRequest).mockResolvedValueOnce({
      accountId: "staff-1",
      email: "doctor@example.com",
      temporaryPassword: "once",
    });
    await createLiveStaffAccount({
      email: "doctor@example.com",
      fullName: "Bác sĩ An",
      role: "ORTHOPEDIC_DOCTOR",
    });
    expect(apiRequest).toHaveBeenCalledWith("/api/v1/admin/staff-accounts", {
      method: "POST",
      body: JSON.stringify({
        email: "doctor@example.com",
        fullName: "Bác sĩ An",
        role: "DOCTOR",
      }),
    });
  });
});
