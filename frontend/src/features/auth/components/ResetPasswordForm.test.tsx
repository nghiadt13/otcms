import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ResetPasswordForm } from "./ResetPasswordForm";
import { resetAuthSessionForTest } from "@/lib/auth/authSession";

const fetchMock = vi.fn();
beforeEach(() => {
  resetAuthSessionForTest();
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockImplementation((url: string) =>
    Promise.resolve(
      url.endsWith("/csrf")
        ? new Response(JSON.stringify({ csrfToken: "csrf-test" }))
        : new Response(null, { status: 204 }),
    ),
  );
});
afterEach(() => vi.unstubAllGlobals());
async function fill(password = "new-password-123") {
  await userEvent.type(screen.getByLabelText(/^Mật khẩu mới/), password);
  await userEvent.type(
    screen.getByLabelText(/^Nhập lại mật khẩu mới/),
    password,
  );
  await userEvent.click(
    screen.getByRole("button", { name: "Đặt mật khẩu mới" }),
  );
}
describe("ResetPasswordForm", () => {
  it("uses the backend and shows success only after 204", async () => {
    render(<ResetPasswordForm token="test-token" />);
    await fill();
    expect(await screen.findByRole("status")).toHaveTextContent(
      "Mật khẩu đã được cập nhật",
    );
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/patient/reset-password"),
      expect.objectContaining({
        body: JSON.stringify({
          token: "test-token",
          newPassword: "new-password-123",
        }),
      }),
    );
  });
  it("blocks the former eight-character password before calling the API", async () => {
    render(<ResetPasswordForm token="test-token" />);
    await fill("Abcd1234");
    expect(
      screen.getByText("Mật khẩu chưa đạt yêu cầu bên dưới."),
    ).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it("offers a new recovery link when the backend rejects an expired/used token", async () => {
    fetchMock
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ csrfToken: "csrf-test" })),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ error: { code: "PASSWORD_RESET_TOKEN_INVALID" } }),
          { status: 400 },
        ),
      );
    render(<ResetPasswordForm token="expired-token" portal="staff" />);
    await fill();
    expect(await screen.findByRole("alert")).toHaveTextContent("đã hết hạn");
    expect(
      screen.getByRole("link", { name: "Gửi lại hướng dẫn" }),
    ).toHaveAttribute("href", "/forgot-password?portal=staff");
  });
  it("lets the user choose another password after a reuse rejection", async () => {
    fetchMock
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ csrfToken: "csrf-test" })),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ error: { code: "PASSWORD_REUSE_NOT_ALLOWED" } }),
          { status: 400 },
        ),
      );
    render(<ResetPasswordForm token="test-token" />);
    await fill();
    expect(
      await screen.findByText("Mật khẩu mới phải khác mật khẩu hiện tại."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Đặt mật khẩu mới" }),
    ).toBeEnabled();
  });
  it("does not render a password form without a token", () => {
    render(<ResetPasswordForm token="" />);
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Link đặt lại mật khẩu không hợp lệ",
    );
    expect(screen.queryByLabelText(/^Mật khẩu mới/)).not.toBeInTheDocument();
  });
});
