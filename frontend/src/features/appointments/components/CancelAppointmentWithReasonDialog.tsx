"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { APP_MESSAGES } from "@/lib/messages/appMessages";
import { notifyError, notifySuccess } from "@/lib/messages/notify";

/**
 * Hủy lịch do nhân viên thực hiện (lễ tân hoặc admin). `clinicFault` đánh dấu lỗi phía phòng khám
 * (bác sĩ nghỉ, không có người thay): khoản phí đã trả sẽ chờ hoàn tiền. Khu bệnh nhân không dùng
 * hộp thoại này nên không bao giờ gửi cờ đó.
 */
export function CancelAppointmentWithReasonDialog({
  open,
  onClose,
  onConfirm,
}: {
  open: boolean;
  onClose: () => void;
  onConfirm: (reason: string, clinicFault: boolean) => Promise<void>;
}) {
  const [reason, setReason] = useState("");
  const [clinicFault, setClinicFault] = useState(false);
  const [pending, setPending] = useState(false);

  async function submit() {
    if (!reason.trim()) return;
    setPending(true);
    try {
      await onConfirm(reason.trim(), clinicFault);
      notifySuccess("MSG-28");
      setReason("");
      setClinicFault(false);
      onClose();
    } catch (error) {
      notifyError(error);
    } finally {
      setPending(false);
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(value) => {
        if (!value && !pending) onClose();
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Hủy lịch hẹn</DialogTitle>
          <DialogDescription>{APP_MESSAGES["MSG-93"]}</DialogDescription>
        </DialogHeader>
        <label className="grid gap-2">
          Lý do hủy
          <textarea
            className="min-h-24 rounded-md border p-2"
            maxLength={1000}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
        </label>
        <label className="flex items-start gap-3">
          <Checkbox
            checked={clinicFault}
            disabled={pending}
            onCheckedChange={(value) => setClinicFault(value === true)}
          />
          <span>Lỗi phía phòng khám (bác sĩ nghỉ, không có người thay)</span>
        </label>
        <DialogFooter>
          <Button variant="outline" disabled={pending} onClick={onClose}>
            Đóng
          </Button>
          <Button
            disabled={pending || !reason.trim()}
            onClick={() => void submit()}
          >
            Xác nhận hủy
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
