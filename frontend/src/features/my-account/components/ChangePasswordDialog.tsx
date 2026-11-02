"use client";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { ChangePasswordForm } from "./ChangePasswordForm";

type ChangePasswordDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
};

/**
 * Dialog đổi mật khẩu. Render NGOÀI DropdownMenuContent (state nằm ở nơi mở),
 * nếu không dialog bị unmount khi menu đóng. Form chỉ mount khi dialog mở nên
 * đóng dialog là xóa sạch các ô đã nhập.
 */
export function ChangePasswordDialog({
  open,
  onOpenChange,
}: ChangePasswordDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Đổi mật khẩu</DialogTitle>
          <DialogDescription>
            Nhập mật khẩu hiện tại, rồi đặt mật khẩu mới.
          </DialogDescription>
        </DialogHeader>
        <ChangePasswordForm
          mode="voluntary"
          onDone={() => onOpenChange(false)}
          onCancel={() => onOpenChange(false)}
        />
      </DialogContent>
    </Dialog>
  );
}
