import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { resetMyAccountMockStore } from "../api/myAccountApi";
import { ChangePasswordForm } from "./ChangePasswordForm";

vi.mock("@/lib/messages/notify", () => ({
  notifySuccess: vi.fn(),
  notifyError: vi.fn(),
}));

beforeEach(() => {
  resetMyAccountMockStore();
  vi.clearAllMocks();
});

async function fill(current: string, next: string, confirm = next) {
  await userEvent.type(screen.getByLabelText("Mật khẩu hiện tại"), current);
  await userEvent.type(screen.getByLabelText("Mật khẩu mới"), next);
  await userEvent.type(screen.getByLabelText("Nhập lại mật khẩu mới"), confirm);
  await userEvent.click(screen.getByRole("button", { name: "Đổi mật khẩu" }));
}

describe("ChangePasswordForm", () => {
  it("mật khẩu hiện tại sai: lỗi đỏ ngay dưới ô mật khẩu hiện tại", async () => {
    const onDone = vi.fn();
    render(<ChangePasswordForm mode="voluntary" onDone={onDone} />);
    await fill("SaiMatKhau1", "MatKhauMoi2");

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Mật khẩu hiện tại không đúng");
    expect(screen.getByLabelText("Mật khẩu hiện tại")).toHaveAttribute(
      "aria-describedby",
      alert.id,
    );
    expect(onDone).not.toHaveBeenCalled();
  });

  it("thành công: gọi onDone và báo đã đổi mật khẩu", async () => {
    const onDone = vi.fn();
    render(<ChangePasswordForm mode="voluntary" onDone={onDone} />);
    await fill("MatKhauCu1", "MatKhauMoi2");

    await waitFor(() => expect(onDone).toHaveBeenCalledTimes(1));
    expect(notifySuccess).toHaveBeenCalledWith("Mật khẩu đã được thay đổi.");
  });

  it("chặn mật khẩu nhập lại không khớp, không gọi API", async () => {
    const onDone = vi.fn();
    render(<ChangePasswordForm mode="voluntary" onDone={onDone} />);
    await fill("MatKhauCu1", "MatKhauMoi2", "KhacHan3");

    expect(await screen.findByRole("alert")).toHaveTextContent("chưa khớp");
    expect(onDone).not.toHaveBeenCalled();
  });

  it("lỗi dịch vụ: toast, không có lỗi dưới ô", async () => {
    render(<ChangePasswordForm mode="voluntary" onDone={vi.fn()} />);
    await fill("LoiDichVu1", "MatKhauMoi2");

    await waitFor(() => expect(notifyError).toHaveBeenCalledTimes(1));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("mode forced không có nút Hủy; voluntary có", () => {
    const { unmount } = render(<ChangePasswordForm mode="forced" onDone={vi.fn()} />);
    expect(screen.queryByRole("button", { name: "Hủy" })).not.toBeInTheDocument();
    unmount();
    render(<ChangePasswordForm mode="voluntary" onDone={vi.fn()} />);
    expect(screen.getByRole("button", { name: "Hủy" })).toBeInTheDocument();
  });

  it("checklist cập nhật theo lúc gõ; nút hiện/ẩn có aria-label", async () => {
    render(<ChangePasswordForm mode="forced" onDone={vi.fn()} />);
    const list = screen.getByRole("list", { name: "Yêu cầu mật khẩu mới" });
    expect(list).toHaveTextContent("Tối thiểu 8 ký tự(chưa đạt)");
    await userEvent.type(screen.getByLabelText("Mật khẩu mới"), "abcd1234");
    expect(list).toHaveTextContent("Tối thiểu 8 ký tự(đạt)");

    const toggles = screen.getAllByRole("button", { name: "Hiện mật khẩu" });
    expect(toggles).toHaveLength(3);
    await userEvent.click(toggles[0]);
    expect(screen.getByLabelText("Mật khẩu hiện tại")).toHaveAttribute("type", "text");
    expect(screen.getByRole("button", { name: "Ẩn mật khẩu" })).toBeInTheDocument();
  });

  it("khi đang gửi: nút 'Đang lưu...' bị khóa", async () => {
    render(<ChangePasswordForm mode="forced" onDone={vi.fn()} />);
    await userEvent.type(screen.getByLabelText("Mật khẩu hiện tại"), "MatKhauCu1");
    await userEvent.type(screen.getByLabelText("Mật khẩu mới"), "MatKhauMoi2");
    await userEvent.type(screen.getByLabelText("Nhập lại mật khẩu mới"), "MatKhauMoi2");
    // fireEvent chạy đồng bộ nên thấy trạng thái đang gửi trước khi mock trả về.
    fireEvent.click(screen.getByRole("button", { name: "Đổi mật khẩu" }));
    expect(screen.getByRole("button", { name: "Đang lưu..." })).toBeDisabled();
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Đổi mật khẩu" })).toBeEnabled(),
    );
  });
});
