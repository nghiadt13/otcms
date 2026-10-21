import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ConfirmActionDialog } from "./ConfirmActionDialog";

function setup(onConfirm: () => Promise<void>, onOpenChange = vi.fn()) {
  render(
    <ConfirmActionDialog
      open
      onOpenChange={onOpenChange}
      title="Vô hiệu hóa tài khoản"
      description="Người dùng sẽ không đăng nhập được."
      confirmLabel="Vô hiệu hóa"
      pendingLabel="Đang vô hiệu hóa..."
      destructive
      onConfirm={onConfirm}
    />,
  );
  return onOpenChange;
}

describe("ConfirmActionDialog", () => {
  it("khóa nút khi đang chạy rồi đóng khi thành công", async () => {
    let finish: () => void = () => {};
    const onOpenChange = setup(
      () => new Promise<void>((resolve) => (finish = resolve)),
    );
    await userEvent.click(screen.getByRole("button", { name: "Vô hiệu hóa" }));
    expect(
      screen.getByRole("button", { name: "Đang vô hiệu hóa..." }),
    ).toBeDisabled();
    finish();
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
  });

  it("giữ dialog mở khi onConfirm ném lỗi", async () => {
    const onOpenChange = setup(() => Promise.reject(new Error("x")));
    await userEvent.click(screen.getByRole("button", { name: "Vô hiệu hóa" }));
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "Vô hiệu hóa" }),
      ).toBeEnabled(),
    );
    expect(onOpenChange).not.toHaveBeenCalledWith(false);
  });
});
