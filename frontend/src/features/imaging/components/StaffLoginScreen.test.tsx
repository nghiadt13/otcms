import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { StaffLoginScreen } from "./StaffLoginScreen";

const { replace, signInWithPassword, signOut } = vi.hoisted(() => ({
  replace: vi.fn(),
  signInWithPassword: vi.fn(),
  signOut: vi.fn(),
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));
vi.mock("@/lib/auth/authSession", () => ({ signInWithPassword, signOut }));

beforeEach(() => {
  replace.mockReset();
  signInWithPassword.mockReset();
  signOut.mockReset();
});

async function logIn() {
  const user = userEvent.setup();
  await user.type(
    screen.getByPlaceholderText("Nhập email hoặc tên đăng nhập"),
    "ktv.hinhanh",
  );
  await user.type(
    screen.getByPlaceholderText("Nhập mật khẩu"),
    "MauMatKhau123!",
  );
  await user.click(screen.getByRole("button", { name: "Đăng nhập" }));
}

describe("StaffLoginScreen", () => {
  it("KTV hợp lệ vào khu hình ảnh", async () => {
    signInWithPassword.mockResolvedValue({
      accountType: "IMAGING_TECHNICIAN",
      mustChangePassword: false,
    });
    render(<StaffLoginScreen />);
    await logIn();
    expect(signInWithPassword).toHaveBeenCalledWith(
      "staff",
      "ktv.hinhanh",
      "MauMatKhau123!",
    );
    expect(replace).toHaveBeenCalledWith("/imaging");
  });

  it("mật khẩu khởi tạo chuyển đến đổi mật khẩu", async () => {
    signInWithPassword.mockResolvedValue({
      accountType: "IMAGING_TECHNICIAN",
      mustChangePassword: true,
    });
    render(<StaffLoginScreen />);
    await logIn();
    expect(replace).toHaveBeenCalledWith("/imaging/change-password");
  });

  it("vai trò khác không được giữ phiên ở khu hình ảnh", async () => {
    signInWithPassword.mockResolvedValue({
      accountType: "RECEPTIONIST",
      mustChangePassword: false,
    });
    render(<StaffLoginScreen />);
    await logIn();
    expect(signOut).toHaveBeenCalledOnce();
    expect(replace).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "không thuộc kỹ thuật viên",
    );
    await user.type(screen.getByPlaceholderText("Nhập mật khẩu"), "temporary");
    await user.click(screen.getByRole("button", { name: "Đăng nhập" }));

    expect(nav.push).toHaveBeenCalledWith("/first-login-password");
  });
});
