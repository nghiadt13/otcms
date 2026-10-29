import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  getMockSignedInKind,
  setMockSignedInKind,
} from "@/lib/session/mockSignedInKind";
import { AuthSessionError } from "@/lib/auth/authSession";
import { authMessages } from "../authMessages";
import { SignInForm } from "./SignInForm";

const { replace, signInWithPassword } = vi.hoisted(() => ({
  replace: vi.fn(),
  signInWithPassword: vi.fn(),
}));

// Ranh giới ngoài duy nhất được giả: lời gọi đăng nhập tới backend. Luồng `signIn` thật vẫn chạy.
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));
vi.mock("@/lib/auth/authSession", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/auth/authSession")>()),
  signInWithPassword,
}));

beforeEach(() => {
  replace.mockClear();
  signInWithPassword.mockReset();
  signInWithPassword.mockResolvedValue({ accountType: "PATIENT" });
  window.localStorage.clear();
});

async function submitCredentials(button = "Đăng nhập") {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText(/^Email/), "an@example.com");
  await user.type(screen.getByLabelText(/^Mật khẩu/), "matkhau123");
  await user.click(screen.getByRole("button", { name: button }));
}

describe("SignInForm: đích sau đăng nhập", () => {
  it("không có next thì về trang chủ", async () => {
    render(<SignInForm />);
    await submitCredentials();
    expect(replace).toHaveBeenCalledWith("/");
  });

  it.each([
    [
      "đường dẫn trong khu bệnh nhân",
      "/portal/appointments/new",
      "/portal/appointments/new",
    ],
    ["URL ngoài", "https://evil.example", "/"],
    ["URL giao thức tương đối", "//evil.example", "/"],
    ["khu quản trị", "/admin", "/"],
  ])("next = %s (%s) → %s", async (_label, next, target) => {
    render(<SignInForm next={next} />);
    await submitCredentials();
    expect(replace).toHaveBeenCalledWith(target);
  });

  it("đăng nhập thành công ghi phiên giả bệnh nhân", async () => {
    render(<SignInForm />);
    expect(getMockSignedInKind()).toBe("STAFF");
    await submitCredentials();
    expect(getMockSignedInKind()).toBe("PATIENT");
  });

  it("mở màn đăng nhập xóa phiên giả cũ (đăng xuất đi qua đây)", () => {
    setMockSignedInKind("PATIENT");
    render(<SignInForm />);
    expect(getMockSignedInKind()).toBe("STAFF");
  });

  it("nhãn nút lấy từ submitLabel (dùng sau đăng ký)", () => {
    render(<SignInForm submitLabel="Vào cổng bệnh nhân" />);
    expect(
      screen.getByRole("button", { name: "Vào cổng bệnh nhân" }),
    ).toBeInTheDocument();
  });
});

describe("SignInForm: đăng nhập nội bộ (staff)", () => {
  it("gọi endpoint nhân viên rồi về /workspace, không ghi phiên giả bệnh nhân", async () => {
    render(<SignInForm staff />);
    await submitCredentials();
    expect(signInWithPassword).toHaveBeenCalledWith(
      "staff",
      "an@example.com",
      "matkhau123",
    );
    expect(replace).toHaveBeenCalledWith("/workspace");
    expect(getMockSignedInKind()).toBe("STAFF");
  });

  it("nhận email tài khoản dùng thử dạng ten@localhost", async () => {
    const user = userEvent.setup();
    render(<SignInForm staff />);
    await user.type(screen.getByLabelText(/^Email/), "admin@localhost");
    await user.type(screen.getByLabelText(/^Mật khẩu/), "matkhau123");
    await user.click(screen.getByRole("button", { name: "Đăng nhập" }));
    expect(signInWithPassword).toHaveBeenCalledWith(
      "staff",
      "admin@localhost",
      "matkhau123",
    );
  });

  it("không hiện liên kết Quên mật khẩu", () => {
    render(<SignInForm staff />);
    expect(screen.queryByText("Quên mật khẩu?")).not.toBeInTheDocument();
  });

  it("backend từ chối (401) thì hiện MSG-01 và không chuyển trang", async () => {
    signInWithPassword.mockRejectedValue(new AuthSessionError(401));
    render(<SignInForm staff />);
    await submitCredentials();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      authMessages["MSG-01"],
    );
    expect(replace).not.toHaveBeenCalled();
  });
});

describe("SignInForm: lỗi", () => {
  it("backend từ chối (401) thì hiện MSG-01 và không chuyển trang", async () => {
    signInWithPassword.mockRejectedValue(new AuthSessionError(401));
    render(<SignInForm />);
    await submitCredentials();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      authMessages["MSG-01"],
    );
    expect(replace).not.toHaveBeenCalled();
    expect(getMockSignedInKind()).toBe("STAFF");
  });

  it("lỗi dịch vụ (không phải 401) thì hiện MSG-04", async () => {
    signInWithPassword.mockRejectedValue(new AuthSessionError(503));
    render(<SignInForm />);
    await submitCredentials();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      authMessages["MSG-04"],
    );
    expect(replace).not.toHaveBeenCalled();
  });
});
