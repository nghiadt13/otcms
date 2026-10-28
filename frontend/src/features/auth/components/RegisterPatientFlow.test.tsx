import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { authMessages } from "../authMessages";
import { RegisterPatientFlow } from "./RegisterPatientFlow";

// Bước 3 dùng SignInForm (cần router của Next); chỉ giả router, còn lại đi qua
// API giả thật như màn hình chạy.
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn() }),
}));

const fetchMock = vi.fn<typeof fetch>();
beforeEach(() => {
  fetchMock.mockReset();
  fetchMock.mockImplementation(async (input, init) => {
    const url = String(input);
    if (url.endsWith("/api/auth/patient/register"))
      return new Response(JSON.stringify({ message: "Verification requested" }), { status: 202 });
    if (url.endsWith("/api/auth/patient/verify-email")) {
      const payload = JSON.parse(String(init?.body)) as { otp: string };
      return payload.otp === "000000"
        ? new Response(JSON.stringify({ error: { code: "OTP_INVALID_OR_EXPIRED" } }), { status: 400 })
        : new Response(null, { status: 204 });
    }
    return new Response(null, { status: 202 });
  });
  vi.stubGlobal("fetch", fetchMock);
});
afterEach(() => vi.unstubAllGlobals());

async function fillDetails(email: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText(/Họ và tên/), "Nguyễn Văn An");
  await user.type(screen.getByLabelText(/^Email/), email);
  await user.type(screen.getByLabelText(/^Mật khẩu/), "matkhau123");
  await user.type(screen.getByLabelText(/Nhập lại mật khẩu/), "matkhau123");
  expect(screen.getByText("Mật khẩu khớp")).toBeInTheDocument();
  await user.click(screen.getByRole("checkbox", { name: /Tôi đồng ý/ }));
  await user.click(screen.getByRole("button", { name: "Tiếp tục" }));
  return user;
}

describe("RegisterPatientFlow", () => {
  it("không hỏi ngày sinh, giới tính hay số điện thoại (đó là hồ sơ bệnh nhân, UC13)", () => {
    render(<RegisterPatientFlow />);
    expect(screen.queryByLabelText(/Ngày sinh/)).toBeNull();
    expect(screen.queryByLabelText(/Giới tính/)).toBeNull();
    expect(screen.queryByLabelText(/Số điện thoại/)).toBeNull();
  });

  it("báo lỗi MSG-08 và giữ ở bước 1 khi thiếu thông tin", async () => {
    render(<RegisterPatientFlow />);
    await userEvent.click(screen.getByRole("button", { name: "Tiếp tục" }));

    expect(screen.getByRole("alert")).toHaveTextContent(authMessages["MSG-08"]);
    expect(screen.getByText("Nhập họ và tên.")).toBeInTheDocument();
    expect(
      screen.getByText("Bạn cần đồng ý để tạo tài khoản."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Tạo tài khoản bệnh nhân" }),
    ).toBeInTheDocument();
  });

  it("chỉ báo lỗi khi rời ô, không báo lúc đang gõ", async () => {
    const user = userEvent.setup();
    render(<RegisterPatientFlow />);
    const email = screen.getByLabelText(/^Email/);

    await user.type(email, "an@");
    expect(screen.queryByText("Email chưa đúng định dạng.")).toBeNull();

    await user.tab();
    expect(screen.getByText("Email chưa đúng định dạng.")).toBeInTheDocument();

    await user.type(email, "example.com");
    expect(screen.queryByText("Email chưa đúng định dạng.")).toBeNull();
  });

  it("đi đủ 3 bước: thông tin → xác minh email → vào cổng bệnh nhân", async () => {
    render(<RegisterPatientFlow />);
    const user = await fillDetails("an@example.com");

    expect(
      await screen.findByRole("heading", { name: "Xác minh email" }),
    ).toBeInTheDocument();
    expect(screen.getByText("an@example.com")).toBeInTheDocument();

    await user.type(screen.getByLabelText(/Mã xác minh/), "000000");
    await user.click(screen.getByRole("button", { name: "Xác minh" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      authMessages["MSG-08"],
    );

    await user.clear(screen.getByLabelText(/Mã xác minh/));
    await user.type(screen.getByLabelText(/Mã xác minh/), "123456");
    await user.click(screen.getByRole("button", { name: "Xác minh" }));

    expect(
      await screen.findByRole("heading", { name: "Đăng ký hoàn tất" }),
    ).toBeInTheDocument();
    expect(screen.getByText(authMessages["MSG-10"])).toBeInTheDocument();
    expect(screen.getByLabelText(/^Email/)).toHaveValue("an@example.com");
    expect(
      screen.getByRole("button", { name: "Vào cổng bệnh nhân" }),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/auth/patient/register",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          fullName: "Nguyễn Văn An",
          email: "an@example.com",
          password: "matkhau123",
          confirmPassword: "matkhau123",
          patientConsent: true,
        }),
      }),
    );
  }, 15000);
});
