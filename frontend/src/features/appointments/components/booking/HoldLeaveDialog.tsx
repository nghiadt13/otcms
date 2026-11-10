"use client";

import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { cn } from "@/lib/utils";
import { HOLD_LEAVE_DIALOG } from "../../schema/paymentHoldMessages";

// Hộp thoại khi rời bước thanh toán còn giữ chỗ (bấm liên kết sang trang khác hoặc Back). Nút cao
// h-12 như PatientCancelDialog cho người dùng lớn tuổi. Đang hủy thì khóa cả hai nút.
export function HoldLeaveDialog({
  open,
  pending,
  onStay,
  onLeave,
}: {
  open: boolean;
  pending: boolean;
  /** "Tiếp tục thanh toán": đóng hộp thoại, ở lại. */
  onStay: () => void;
  /** "Hủy lịch": hủy giữ chỗ rồi đi tiếp. */
  onLeave: () => void;
}) {
  return (
    <AlertDialog
      onOpenChange={(next) => {
        if (!next && !pending) onStay();
      }}
      open={open}
    >
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle className="text-xl">
            {HOLD_LEAVE_DIALOG.title}
          </AlertDialogTitle>
          <AlertDialogDescription className="text-base">
            {HOLD_LEAVE_DIALOG.description}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter className="flex-col gap-3 sm:flex-row">
          <AlertDialogCancel
            className={cn(publicButtonClass("primary"), "w-full sm:w-auto")}
            disabled={pending}
          >
            {HOLD_LEAVE_DIALOG.stay}
          </AlertDialogCancel>
          <button
            className={cn(
              publicButtonClass("secondary"),
              "w-full border-destructive text-destructive hover:text-destructive sm:w-auto",
            )}
            disabled={pending}
            onClick={onLeave}
            type="button"
          >
            {pending ? HOLD_LEAVE_DIALOG.leaving : HOLD_LEAVE_DIALOG.leave}
          </button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
