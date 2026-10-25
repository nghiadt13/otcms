"use client";

import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import type { StatusTarget } from "../hooks/useAccountStatusChange";

type AccountStatusDialogProps = {
  target: StatusTarget | null;
  onClose: () => void;
  onConfirm: () => Promise<void>;
};

/** Xác nhận Vô hiệu hóa / Kích hoạt. Vô hiệu hóa chỉ chặn đăng nhập, không xóa dữ liệu. */
export function AccountStatusDialog({
  target,
  onClose,
  onConfirm,
}: AccountStatusDialogProps) {
  // Giữ nội dung lần cuối trong lúc dialog chạy hiệu ứng đóng.
  const deactivate = target?.action !== "activate";
  return (
    <ConfirmActionDialog
      open={target !== null}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
      title={deactivate ? "Vô hiệu hóa tài khoản?" : "Kích hoạt tài khoản?"}
      description={
        deactivate
          ? `${target?.label ?? "Người dùng"} sẽ không đăng nhập được. Dữ liệu và hồ sơ liên quan được giữ nguyên.`
          : `${target?.label ?? "Người dùng"} sẽ đăng nhập được trở lại.`
      }
      confirmLabel={deactivate ? "Vô hiệu hóa" : "Kích hoạt"}
      pendingLabel={deactivate ? "Đang vô hiệu hóa..." : "Đang kích hoạt..."}
      destructive={deactivate}
      onConfirm={onConfirm}
    />
  );
}
