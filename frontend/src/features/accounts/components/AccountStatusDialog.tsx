"use client";

import { useState } from "react";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import type { AccountStatus, UserAccount } from "../types/account";

type AccountStatusDialogProps = {
  account: UserAccount | null;
  targetStatus: AccountStatus | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: (account: UserAccount, newStatus: AccountStatus) => Promise<void>;
};

export function AccountStatusDialog({
  account,
  targetStatus,
  open,
  onOpenChange,
  onConfirm,
}: AccountStatusDialogProps) {
  const [submitting, setSubmitting] = useState(false);

  if (!account || !targetStatus) return null;

  const isLocking = targetStatus === "inactive";

  async function handleConfirm() {
    if (!account || !targetStatus) return;
    setSubmitting(true);
    try {
      await onConfirm(account, targetStatus);
      onOpenChange(false);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>
            {isLocking ? "Khóa tài khoản người dùng?" : "Mở khóa tài khoản?"}
          </AlertDialogTitle>
          <AlertDialogDescription>
            {isLocking ? (
              <>
                Tài khoản của <strong>{account.fullName}</strong> ({account.email}) sẽ
                bị vô hiệu hóa và không thể đăng nhập. Toàn bộ dữ liệu ca trực, lịch hẹn
                và bệnh án liên quan vẫn được giữ nguyên vẹn trên hệ thống (BR-15).
              </>
            ) : (
              <>
                Tài khoản của <strong>{account.fullName}</strong> ({account.email}) sẽ
                được mở khóa để người dùng có thể tiếp tục đăng nhập và làm việc.
              </>
            )}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={submitting}>Hủy</AlertDialogCancel>
          <AlertDialogAction
            variant={isLocking ? "destructive" : "default"}
            onClick={handleConfirm}
            disabled={submitting}
          >
            {submitting
              ? "Đang cập nhật..."
              : isLocking
                ? "Xác nhận khóa"
                : "Mở khóa tài khoản"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
