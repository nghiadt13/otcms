"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { confirmRefund, type RefundItem } from "@/lib/api/refundsApi";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { notifyError, notifySuccess } from "@/lib/messages/notify";

/**
 * Xác nhận đã hoàn tiền cho một khoản chờ hoàn. Phòng khám hoàn ngoài hệ thống; ghi chú tùy chọn
 * (ví dụ hình thức hoàn). Thành công → MSG-96 và đóng hộp thoại.
 */
export function RefundConfirmDialog({
  item,
  onClose,
  onConfirmed,
}: {
  item: RefundItem;
  onClose: () => void;
  onConfirmed: () => void;
}) {
  const [note, setNote] = useState("");
  const [pending, setPending] = useState(false);

  async function confirm() {
    setPending(true);
    try {
      await confirmRefund(item.itemId, note);
      notifySuccess("MSG-96", {
        amount: formatCurrencyVnd(item.amount),
        patientName: item.patientName,
      });
      onConfirmed();
    } catch (error) {
      notifyError(error);
    } finally {
      setPending(false);
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && !pending && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Xác nhận đã hoàn tiền?</DialogTitle>
          <DialogDescription>
            {item.patientName} · {item.description} ·{" "}
            {formatCurrencyVnd(item.amount)}. Chỉ xác nhận sau khi đã trả tiền
            cho bệnh nhân ngoài hệ thống.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-1.5">
          <Label htmlFor="refund-note">Ghi chú (tùy chọn)</Label>
          <Textarea
            id="refund-note"
            maxLength={500}
            value={note}
            disabled={pending}
            onChange={(event) => setNote(event.target.value)}
          />
        </div>
        <DialogFooter>
          <Button variant="outline" disabled={pending} onClick={onClose}>
            Hủy
          </Button>
          <Button disabled={pending} onClick={() => void confirm()}>
            {pending ? "Đang xác nhận..." : "Đã hoàn tiền"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
