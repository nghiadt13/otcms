import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, afterEach, it, expect, vi } from "vitest";
import { resetAuthSessionForTest } from "@/lib/auth/authSession";
import { ResetPasswordForm } from "./ResetPasswordForm";

const fetchMock = vi.fn();
beforeEach(() => {
  resetAuthSessionForTest();
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockImplementation((url: string) => Promise.resolve(url.endsWith("/csrf") ? new Response(JSON.stringify({ csrfToken: "csrf-test" })) : new Response(null, { status: 204 })));
});
afterEach(() => vi.unstubAllGlobals());
async function fill() {
  await userEvent.type(screen.getByLabelText(/^Mã OTP/), "123456");
  await userEvent.type(screen.getByLabelText(/^Mật khẩu mới/), "Password1");
  await userEvent.type(screen.getByLabelText(/^Nhập lại mật khẩu mới/), "Password1");
  await userEvent.click(screen.getByRole("button", { name: "Đặt mật khẩu mới" }));
}
it("submits email, OTP and new password", async () => {
  render(<ResetPasswordForm email="a@example.com" onRestart={() => {}} />);
  await fill();
  expect(await screen.findByRole("status")).toHaveTextContent("Mật khẩu đã được cập nhật");
  expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining("/patient/reset-password"), expect.objectContaining({ body: JSON.stringify({email: "a@example.com", otp: "123456", newPassword: "Password1"}) }));
});
it("shows MSG-77 and lets the patient restart after an invalid OTP", async () => {
  const restart = vi.fn();
  fetchMock.mockImplementation((url: string) => Promise.resolve(url.endsWith("/csrf") ? new Response(JSON.stringify({ csrfToken: "csrf-test" })) : new Response(JSON.stringify({ error: { code: "MSG-77" } }), {status: 400})));
  render(<ResetPasswordForm email="a@example.com" onRestart={restart} />);
  await fill();
  expect(await screen.findByRole("alert")).toHaveTextContent("Mã OTP không đúng");
  await userEvent.click(screen.getByRole("button", {name: "Yêu cầu mã mới"}));
  expect(restart).toHaveBeenCalledOnce();
});
