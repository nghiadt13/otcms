"use client";

import { useState } from "react";
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

// Nút "Hủy thanh toán" của bước thanh toán đặt lịch: hỏi xác nhận rồi gọi `onConfirm`. Việc hủy
// thật (gọi BE, quay về bước 1) do nơi ghép làm. Chữ cố định chưa có mã MSG.

export function CancelPaymentHoldButton({
  onConfirm,
}: {
  onConfirm: () => void | Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const [failed, setFailed] = useState(false);

  async function handleConfirm() {
    setPending(true);
    setFailed(false);
    try {
      await onConfirm();
      setOpen(false);
    } catch {
      // Giữ hộp thoại để người dùng thử lại hoặc tiếp tục thanh toán.
      setFailed(true);
    } finally {
      setPending(false);
    }
  }

  return (
    <>
      <button
        className={publicButtonClass("secondary")}
        onClick={() => {
          setFailed(false);
          setOpen(true);
        }}
        type="button"
      >
        Hủy thanh toán
      </button>
      <AlertDialog
        onOpenChange={(next) => {
          if (!pending) setOpen(next);
        }}
        open={open}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle className="text-xl">
              Bạn có chắc muốn hủy thanh toán?
            </AlertDialogTitle>
            <AlertDialogDescription className="text-base">
              Lịch hẹn đang giữ chỗ sẽ bị hủy và giờ khám được mở lại cho người
              khác.
            </AlertDialogDescription>
          </AlertDialogHeader>
          {failed ? (
            <p className="text-sm text-destructive" role="alert">
              Chưa hủy được. Vui lòng thử lại.
            </p>
          ) : null}
          <AlertDialogFooter className="flex-col gap-3 sm:flex-row">
            <AlertDialogCancel
              className={cn(publicButtonClass("secondary"), "w-full sm:w-auto")}
              disabled={pending}
            >
              Tiếp tục thanh toán
            </AlertDialogCancel>
            <button
              className={cn(
                publicButtonClass("secondary"),
                "w-full border-destructive text-destructive hover:text-destructive sm:w-auto",
              )}
              disabled={pending}
              onClick={() => void handleConfirm()}
              type="button"
            >
              {pending ? "Đang hủy..." : "Hủy lịch"}
            </button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
