import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { Toaster } from "sonner";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { isPasswordValid } from "@/lib/domain/passwordPolicy";
import { resetAccountMockStore } from "../api/accountMockData";
import { AccountFormDialog } from "./AccountFormDialog";
import { formatAppMessage } from "@/lib/messages/appMessages";
const onCreated = vi.fn();
const onClose = vi.fn();

// Mount có điều kiện như màn thật: đóng là gỡ Dialog, mật khẩu biến khỏi DOM.
function Harness() {
  const [open, setOpen] = useState(true);
  return (
    <>
      <Toaster />
      {open ? (
        <AccountFormDialog
          onCreated={onCreated}
          onClose={() => {
            onClose();
            setOpen(false);
          }}
        />
      ) : null}
    </>
  );
}

async function fill(
  user: ReturnType<typeof userEvent.setup>,
  username: string,
  password: string,
) {
  await user.type(screen.getByLabelText("Tên đăng nhập *"), username);
  await user.type(screen.getByLabelText("Mật khẩu khởi tạo *"), password);
}

beforeEach(() => {
  resetAccountMockStore();
  onCreated.mockClear();
  onClose.mockClear();
});

describe("AccountFormDialog", () => {
  it("tạo thành công -> bước 2 hiện mật khẩu một lần, sao chép, Xong thì mật khẩu mất", async () => {
    const user = userEvent.setup();
    const writeText = vi.spyOn(navigator.clipboard, "writeText");
    render(<Harness />);

    await user.click(screen.getByRole("radio", { name: "Bác sĩ chỉnh hình" }));
    await fill(user, "bacsi.moi", "Xk7mQ2pL9vRt");
    await user.click(screen.getByRole("button", { name: "Tạo tài khoản" }));

    expect(
      await screen.findByText(
        formatAppMessage("MSG-11", { accountRole: "Bác sĩ chỉnh hình", username: "bacsi.moi" }),
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("Xk7mQ2pL9vRt")).toBeInTheDocument();
    expect(screen.getByText(/Đóng hộp thoại này là mất mật khẩu/)).toBeInTheDocument();
    expect(onCreated).toHaveBeenCalledTimes(1);
    // Bước kết quả không có nút X.
    expect(screen.queryByRole("button", { name: "Đóng" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Sao chép mật khẩu" }));
    expect(writeText).toHaveBeenCalledWith("Xk7mQ2pL9vRt");
    expect(await screen.findByRole("button", { name: "Đã sao chép" })).toBeInTheDocument();

    // Esc không đóng bước kết quả.
    await user.keyboard("{Escape}");
    expect(screen.getByText("Xk7mQ2pL9vRt")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Xong" }));
    await waitFor(() =>
      expect(screen.queryByText("Xk7mQ2pL9vRt")).not.toBeInTheDocument(),
    );
    expect(onClose).toHaveBeenCalled();
  });

  it("tên đăng nhập trùng -> toast và giữ form với giá trị đã nhập", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await fill(user, "trung", "Xk7mQ2pL9vRt");
    await user.click(screen.getByRole("button", { name: "Tạo tài khoản" }));

    expect(
      await screen.findAllByText(
        formatAppMessage("MSG-13"),
      ),
    ).not.toHaveLength(0);
    expect(screen.getByLabelText("Tên đăng nhập *")).toHaveValue("trung");
    expect(screen.getByLabelText("Mật khẩu khởi tạo *")).toHaveValue("Xk7mQ2pL9vRt");
    expect(onClose).not.toHaveBeenCalled();
  });

  it("chặn trước khi gửi: lỗi đỏ dưới đúng ô, chưa gọi API", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await fill(user, "Le An", "abc");
    await user.click(screen.getByRole("button", { name: "Tạo tài khoản" }));

    const alerts = await screen.findAllByRole("alert");
    expect(alerts.map((a) => a.textContent).join(" ")).toContain("Tên đăng nhập chưa đúng quy tắc");
    expect(alerts.map((a) => a.textContent).join(" ")).toContain("Mật khẩu chưa đạt");
    expect(onCreated).not.toHaveBeenCalled();
  });

  it("không đủ quyền (403) -> toast và đóng form", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await fill(user, "khong.quyen", "Xk7mQ2pL9vRt");
    await user.click(screen.getByRole("button", { name: "Tạo tài khoản" }));

    expect(
      await screen.findByText(formatAppMessage("MSG-14")),
    ).toBeInTheDocument();
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(onCreated).not.toHaveBeenCalled();
  });

  it("kết quả không chắc (500) -> toast, đóng form và tải lại danh sách", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await fill(user, "loi.tao", "Xk7mQ2pL9vRt");
    await user.click(screen.getByRole("button", { name: "Tạo tài khoản" }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(onCreated).toHaveBeenCalledTimes(1);
    expect(
      await screen.findByText(formatAppMessage("MSG-16")),
    ).toBeInTheDocument();
  });

  it("Tạo ngẫu nhiên điền mật khẩu đạt quy tắc; không có ô email hay lựa chọn Bệnh nhân", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole("button", { name: "Tạo ngẫu nhiên" }));

    const value = (screen.getByLabelText("Mật khẩu khởi tạo *") as HTMLInputElement).value;
    expect(isPasswordValid(value)).toBe(true);
    expect(screen.queryByLabelText(/email/i)).not.toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: "Bệnh nhân" })).not.toBeInTheDocument();
    expect(screen.getAllByRole("radio")).toHaveLength(4);
  });
});
