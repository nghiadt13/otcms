import { expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CreateStaffAccountDialog } from "./CreateStaffAccountDialog";

const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));

it("gửi tên đăng nhập và mật khẩu khởi tạo theo hợp đồng API nhân viên", async () => {
  request.mockResolvedValue({ accountId: "a", username: "lab.tech", role: "LAB_TECHNICIAN" });
  render(<CreateStaffAccountDialog open onOpenChange={vi.fn()} />);
  const user = userEvent.setup();
  await user.type(screen.getByLabelText("Tên đăng nhập"), "lab.tech");
  await user.type(screen.getByLabelText("Mật khẩu khởi tạo"), "Password123");
  await user.selectOptions(screen.getByLabelText("Vai trò"), "LAB_TECHNICIAN");
  await user.click(screen.getByRole("button", { name: "Cấp tài khoản" }));
  expect(request).toHaveBeenCalledWith("/api/v1/admin/staff-accounts", {
    method: "POST",
    body: JSON.stringify({ username: "lab.tech", initialPassword: "Password123", role: "LAB_TECHNICIAN" }),
  });
  expect(await screen.findByText("Password123")).toBeInTheDocument();
});
