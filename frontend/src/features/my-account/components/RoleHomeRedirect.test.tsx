import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { RoleHomeRedirect } from "./RoleHomeRedirect";

const { replace, fetchCurrentAccount } = vi.hoisted(() => ({
  replace: vi.fn(),
  fetchCurrentAccount: vi.fn(),
}));

// Ranh giới ngoài: router và lời gọi `GET /api/v1/me`. Việc chọn khu theo vai trò chạy thật.
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));
vi.mock("@/lib/auth/currentAccountApi", () => ({ fetchCurrentAccount }));

const accountOf = (accountType: string) => ({
  id: "11111111-1111-1111-1111-111111111111",
  accountType,
  email: "nguoi.dung@example.com",
  displayName: "Người dùng",
});

beforeEach(() => {
  replace.mockClear();
  fetchCurrentAccount.mockReset();
});

describe("RoleHomeRedirect", () => {
  it("chưa có phiên thì về đăng nhập", async () => {
    fetchCurrentAccount.mockResolvedValue(null);
    render(<RoleHomeRedirect />);
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"));
  });

  it("backend trả 401 thì về đăng nhập", async () => {
    fetchCurrentAccount.mockRejectedValue(new ApiError(401));
    render(<RoleHomeRedirect />);
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"));
  });

  it.each([
    ["PATIENT", "/portal"],
    ["ADMIN", "/admin"],
    ["OWNER", "/admin"],
    ["RECEPTIONIST", "/reception"],
  ])("tài khoản %s vào %s", async (accountType, route) => {
    fetchCurrentAccount.mockResolvedValue(accountOf(accountType));
    render(<RoleHomeRedirect />);
    await waitFor(() => expect(replace).toHaveBeenCalledWith(route));
  });

  it("lỗi đọc tài khoản: hiện thông báo kèm nút thử lại, thử lại thì chuyển trang", async () => {
    fetchCurrentAccount
      .mockRejectedValueOnce(new ApiError(500, { code: "MSG-04" }))
      .mockResolvedValueOnce(accountOf("PATIENT"));
    render(<RoleHomeRedirect />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      formatAppMessage("MSG-04"),
    );
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/portal"));
  });
});
