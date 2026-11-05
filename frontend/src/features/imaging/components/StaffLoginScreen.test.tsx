import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { StaffLoginScreen } from "./StaffLoginScreen";

const auth = vi.hoisted(() => ({ signInWithPassword: vi.fn() }));
const nav = vi.hoisted(() => ({ push: vi.fn() }));

vi.mock("@/lib/auth/authSession", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/auth/authSession")>()),
  signInWithPassword: auth.signInWithPassword,
}));
vi.mock("next/navigation", () => ({ useRouter: () => nav }));

describe("StaffLoginScreen", () => {
  beforeEach(() => {
    auth.signInWithPassword
      .mockReset()
      .mockResolvedValue({ mustChangePassword: false });
    nav.push.mockReset();
  });

  it("creates a real staff session and routes to the technician workspace", async () => {
    const user = userEvent.setup();
    render(<StaffLoginScreen />);

    await user.type(
      screen.getByLabelText("Tên đăng nhập"),
      "tech@example.test",
    );
    await user.type(screen.getByPlaceholderText("Nhập mật khẩu"), "secret");
    await user.click(screen.getByRole("button", { name: "Đăng nhập" }));

    expect(auth.signInWithPassword).toHaveBeenCalledWith(
      "staff",
      "tech@example.test",
      "secret",
    );
    expect(nav.push).toHaveBeenCalledWith("/imaging");
    expect(screen.queryByText(/tài khoản mẫu/i)).not.toBeInTheDocument();
  });

  it("uses the backend first-login password flow when required", async () => {
    auth.signInWithPassword.mockResolvedValueOnce({ mustChangePassword: true });
    const user = userEvent.setup();
    render(<StaffLoginScreen />);

    await user.type(
      screen.getByLabelText("Tên đăng nhập"),
      "tech@example.test",
    );
    await user.type(screen.getByPlaceholderText("Nhập mật khẩu"), "temporary");
    await user.click(screen.getByRole("button", { name: "Đăng nhập" }));

    expect(nav.push).toHaveBeenCalledWith("/first-login-password");
  });
});
