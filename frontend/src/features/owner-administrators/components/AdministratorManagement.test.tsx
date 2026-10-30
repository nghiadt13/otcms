import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import { AdministratorManagement } from "./AdministratorManagement";
vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
const request = vi.mocked(apiRequest);
const account = {
  accountId: "admin-1",
  username: "admin.demo",
  email: null,
  accountType: "ADMIN",
  status: "ACTIVE",
  createdAt: "2026-10-01T00:00:00Z",
  updatedAt: "2026-10-01T00:00:00Z",
};
beforeEach(() => {
  request.mockReset();
  request.mockImplementation(async (path, init) => {
    if (path.endsWith("/deactivate")) return { ...account, status: "DISABLED" };
    if (path.endsWith("/activate")) return { ...account, status: "ACTIVE" };
    if (init?.method === "POST")
      return {
        accountId: account.accountId,
        temporaryPassword: "FixturePassword123!",
        mustChangePassword: true,
      };
    if (path.includes("?"))
      return { content: [account], totalElements: 1, totalPages: 1 };
    return account;
  });
});
it("UC08 AF1 exposes status and reissue actions from account details", async () => {
  const user = userEvent.setup();
  render(<AdministratorManagement />);
  await screen.findByText("admin.demo");
  await user.click(screen.getByRole("button", { name: "Xem tài khoản admin.demo" }));
  const detail = await screen.findByRole("dialog");
  await within(detail).findByText("Quản trị viên");
  expect(within(detail).queryByText("Email")).not.toBeInTheDocument();
  await user.click(
    within(detail).getByRole("button", { name: "Cấp lại mật khẩu" }),
  );
  await screen.findByText("Cấp lại mật khẩu Admin");
  expect(
    request.mock.calls.filter(([, init]) => init?.method === "POST"),
  ).toHaveLength(0);
});
it("UC07 E1 displays invalid input beneath the username field", async () => {
  const user = userEvent.setup();
  render(<AdministratorManagement />);
  await screen.findByText("admin.demo");
  await user.click(screen.getByRole("button", { name: "Tạo tài khoản Admin" }));
  await user.type(screen.getByLabelText("Tên đăng nhập"), "invalid");
  request.mockRejectedValueOnce(new ApiError(400));
  await user.click(screen.getByRole("button", { name: "Tạo tài khoản" }));
  await screen.findByText(
    "Thông tin không hợp lệ. Vui lòng kiểm tra và nhập lại.",
  );
  expect(screen.getByLabelText("Tên đăng nhập")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
});
it("UC09 E1 displays MSG-54 next to search and retains editable criteria", async () => {
  const user = userEvent.setup();
  render(<AdministratorManagement />);
  await screen.findByText("admin.demo");
  request.mockRejectedValueOnce(new ApiError(400));
  const search = screen.getByRole("searchbox", { name: "Tìm tài khoản Admin" });
  await user.type(search, "bad");
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Tiêu chí tìm kiếm không hợp lệ. Vui lòng kiểm tra lại.",
  );
  expect(search).toHaveValue("bad");
  expect(search).toHaveAttribute("aria-invalid", "true");
});
it("creates through the owner API, shows credential once and removes it on close", async () => {
  const user = userEvent.setup();
  render(<AdministratorManagement />);
  await user.click(screen.getByRole("button", { name: "Tạo tài khoản Admin" }));
  await user.type(screen.getByLabelText("Tên đăng nhập"), "admin.new");
  await user.click(screen.getByRole("button", { name: "Tạo tài khoản" }));
  expect(await screen.findByText("FixturePassword123!")).toBeInTheDocument();
  expect(request).toHaveBeenCalledWith("/api/v1/owner/administrator-accounts", {
    method: "POST",
    body: JSON.stringify({ username: "admin.new" }),
  });
  await user.click(screen.getByRole("button", { name: "Đã lưu, đóng" }));
  await waitFor(() =>
    expect(screen.queryByText("FixturePassword123!")).not.toBeInTheDocument(),
  );
  await user.click(screen.getByRole("button", { name: "Tạo tài khoản Admin" }));
  expect(screen.getByLabelText("Tên đăng nhập")).toHaveValue("");
  expect(screen.queryByText("FixturePassword123!")).not.toBeInTheDocument();
});
it("requires confirmation before deactivation and opens details", async () => {
  const user = userEvent.setup();
  render(<AdministratorManagement />);
  await screen.findByText("admin.demo");
  await user.click(screen.getByRole("button", { name: "Xem tài khoản admin.demo" }));
  expect(
    await screen.findByText("Chi tiết tài khoản Admin"),
  ).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Đóng" }));
  await user.click(screen.getByRole("button", { name: "Thao tác cho admin.demo" }));
  await user.click(screen.getByRole("menuitem", { name: "Vô hiệu hóa" }));
  expect(
    request.mock.calls.filter(([, init]) => init?.method === "POST"),
  ).toHaveLength(0);
  await user.click(
    within(screen.getByRole("alertdialog")).getByRole("button", {
      name: "Vô hiệu hóa",
    }),
  );
  await waitFor(() =>
    expect(request).toHaveBeenCalledWith(
      "/api/v1/owner/administrator-accounts/admin-1/deactivate",
      { method: "POST" },
    ),
  );
});
it("reissues only after explicit submission", async () => {
  const user = userEvent.setup();
  render(<AdministratorManagement />);
  await screen.findByText("admin.demo");
  await user.click(screen.getByRole("button", { name: "Thao tác cho admin.demo" }));
  await user.click(screen.getByRole("menuitem", { name: "Cấp lại mật khẩu" }));
  expect(
    request.mock.calls.filter(([, init]) => init?.method === "POST"),
  ).toHaveLength(0);
  await user.click(
    within(screen.getByRole("dialog")).getByRole("button", {
      name: "Cấp lại mật khẩu",
    }),
  );
  await screen.findByText("FixturePassword123!");
  expect(request).toHaveBeenCalledWith(
    "/api/v1/owner/administrator-accounts/admin-1/password-reissues",
    { method: "POST" },
  );
});
it("keeps duplicate username errors in the form without showing a password", async () => {
  const user = userEvent.setup();
  render(<AdministratorManagement />);
  await screen.findByText("admin.demo");
  request.mockRejectedValue(
    new ApiError(409, { errorCode: "USERNAME_ALREADY_EXISTS" }),
  );
  await user.click(screen.getByRole("button", { name: "Tạo tài khoản Admin" }));
  await user.type(screen.getByLabelText("Tên đăng nhập"), "taken");
  await user.click(screen.getByRole("button", { name: "Tạo tài khoản" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Tên đăng nhập này đã được sử dụng",
  );
  expect(screen.getByLabelText("Tên đăng nhập")).toHaveValue("taken");
  expect(screen.queryByLabelText("Mật khẩu khởi tạo")).not.toBeInTheDocument();
});
it("sends search and status filters to the server and displays empty results", async () => {
  const user = userEvent.setup();
  render(<AdministratorManagement />);
  await screen.findByText("admin.demo");
  request.mockResolvedValue({ content: [], totalElements: 0, totalPages: 0 });
  await user.type(
    screen.getByRole("searchbox", { name: "Tìm tài khoản Admin" }),
    "missing",
  );
  await user.selectOptions(
    screen.getByRole("combobox", { name: "Trạng thái" }),
    "DISABLED",
  );
  await screen.findByText(
    "Không tìm thấy tài khoản phù hợp. Hãy thay đổi bộ lọc.",
  );
  expect(request.mock.calls.at(-1)?.[0]).toBe(
    "/api/v1/owner/administrator-accounts?query=missing&page=0&size=10&status=DISABLED",
  );
});
it("shows a recoverable list error", async () => {
  const user = userEvent.setup();
  request.mockRejectedValueOnce(new Error("offline"));
  render(<AdministratorManagement />);
  await screen.findByRole("alert");
  await user.click(screen.getByRole("button", { name: "Thử lại" }));
  expect(await screen.findByText("admin.demo")).toBeInTheDocument();
});
