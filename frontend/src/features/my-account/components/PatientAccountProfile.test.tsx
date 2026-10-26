import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  clearMockSignedInKind,
  setMockSignedInKind,
} from "@/lib/session/mockSignedInKind";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { resetMyAccountMockStore } from "../api/myAccountApi";
import {
  MOCK_CORRECT_CURRENT_PASSWORD,
  MOCK_PATIENT_ACCOUNT,
} from "../api/myAccountMockData";
import { PatientAccountProfile } from "./PatientAccountProfile";
import { PatientChangePasswordPage } from "./PatientChangePasswordPage";

const { replace } = vi.hoisted(() => ({ replace: vi.fn() }));

// Chỉ giả router và đường dẫn; tài khoản và OTP đi qua API giả thật như màn hình chạy.
vi.mock("next/navigation", () => ({
  usePathname: () => "/portal/account",
  useRouter: () => ({ replace, push: vi.fn() }),
}));

beforeEach(() => {
  replace.mockClear();
  setMockSignedInKind("PATIENT");
  resetMyAccountMockStore(MOCK_PATIENT_ACCOUNT);
});

describe("PatientAccountProfile", () => {
  it("hiện tên hiển thị, email và liên kết đổi mật khẩu", async () => {
    render(<PatientAccountProfile />);

    expect(
      screen.getByRole("heading", { level: 1, name: "Tài khoản" }),
    ).toBeInTheDocument();
    expect(await screen.findByText("Nguyễn Văn An")).toBeInTheDocument();
    expect(screen.getByText("an@example.com")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Đổi mật khẩu" })).toHaveAttribute(
      "href",
      "/portal/account/password",
    );
  });

  it("đổi email: OTP sai báo MSG-08; đúng mã thì email mới hiện trên màn", async () => {
    const user = userEvent.setup();
    render(<PatientAccountProfile />);
    await user.click(await screen.findByRole("button", { name: "Đổi email" }));

    await user.type(
      await screen.findByLabelText("Email mới"),
      "moi@example.com",
    );
    await user.click(screen.getByRole("button", { name: "Gửi mã" }));

    await user.type(await screen.findByLabelText("Mã xác minh"), "000000");
    await user.click(screen.getByRole("button", { name: "Xác minh" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      formatAppMessage("MSG-08"),
    );

    await user.clear(screen.getByLabelText("Mã xác minh"));
    await user.type(screen.getByLabelText("Mã xác minh"), "123456");
    await user.click(screen.getByRole("button", { name: "Xác minh" }));

    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(screen.getByText("moi@example.com")).toBeInTheDocument();
    expect(screen.queryByText("an@example.com")).toBeNull();
  });

  it("đổi sang email đã có tài khoản khác: báo MSG-08 ngay ở bước nhập email", async () => {
    const user = userEvent.setup();
    render(<PatientAccountProfile />);
    await user.click(await screen.findByRole("button", { name: "Đổi email" }));

    await user.type(
      await screen.findByLabelText("Email mới"),
      "taken@example.com",
    );
    await user.click(screen.getByRole("button", { name: "Gửi mã" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      formatAppMessage("MSG-08"),
    );
    expect(screen.queryByLabelText("Mã xác minh")).toBeNull();
  });

  it("sửa tên hiển thị: tên quá ngắn báo lỗi, tên hợp lệ thì lưu", async () => {
    const user = userEvent.setup();
    render(<PatientAccountProfile />);
    await user.click(
      await screen.findByRole("button", { name: "Sửa tên hiển thị" }),
    );

    const input = screen.getByLabelText("Tên hiển thị");
    await user.clear(input);
    await user.type(input, "A");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(
      await screen.findByText("Tên hiển thị cần ít nhất 2 ký tự."),
    ).toBeInTheDocument();

    await user.clear(screen.getByLabelText("Tên hiển thị"));
    await user.type(screen.getByLabelText("Tên hiển thị"), "Nguyễn Thị An");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(await screen.findByText("Nguyễn Thị An")).toBeInTheDocument();
  });

  it("phiên nhân viên mở trang bệnh nhân: báo và chỉ lối đăng nhập bệnh nhân", async () => {
    clearMockSignedInKind();
    render(<PatientAccountProfile />);

    expect(
      await screen.findByText("Trang này dành cho tài khoản bệnh nhân."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Đăng nhập bằng tài khoản bệnh nhân" }),
    ).toHaveAttribute("href", "/login");
  });
});

describe("PatientChangePasswordPage", () => {
  it("đổi mật khẩu thành công thì về màn Tài khoản", async () => {
    const user = userEvent.setup();
    render(<PatientChangePasswordPage />);

    await user.type(
      screen.getByLabelText("Mật khẩu hiện tại"),
      MOCK_CORRECT_CURRENT_PASSWORD,
    );
    await user.type(screen.getByLabelText("Mật khẩu mới"), "MatKhauMoi2");
    await user.type(
      screen.getByLabelText("Nhập lại mật khẩu mới"),
      "MatKhauMoi2",
    );
    await user.click(screen.getByRole("button", { name: "Đổi mật khẩu" }));

    await waitFor(() =>
      expect(replace).toHaveBeenCalledWith("/portal/account"),
    );
  });
});
