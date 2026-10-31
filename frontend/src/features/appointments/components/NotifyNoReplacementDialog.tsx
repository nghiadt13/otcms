"use client";

import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";

type NotifyNoReplacementDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Ném lỗi thì dialog giữ mở để thử lại; thành công thì dialog tự đóng. */
  onConfirm: () => Promise<void> | void;
};

/** Xác nhận "Báo cho bệnh nhân" khi không có bác sĩ thay (AF1). Dùng chung admin và Lễ tân. */
export function NotifyNoReplacementDialog({
  open,
  onOpenChange,
  onConfirm,
}: NotifyNoReplacementDialogProps) {
  return (
    <ConfirmActionDialog
      open={open}
      onOpenChange={onOpenChange}
      title="Báo cho bệnh nhân"
      description="Hệ thống sẽ báo cho bệnh nhân rằng bác sĩ không thể khám vào giờ hẹn và bệnh nhân có thể tự hủy lịch hẹn. Lịch hẹn vẫn giữ trạng thái Đã đặt. Thông báo đã gửi không thể thu hồi."
      confirmLabel="Gửi thông báo"
      pendingLabel="Đang gửi..."
      onConfirm={onConfirm}
    />
  );
}
