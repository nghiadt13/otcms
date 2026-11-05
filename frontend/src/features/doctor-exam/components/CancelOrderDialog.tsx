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
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import { Textarea } from "@/components/ui/textarea";
import { getDoctorExamApi } from "../api/doctorExamApi";
import { useAsyncAction } from "../hooks/useAsyncAction";
import type { CancelCause, ParaclinicalOrder } from "../types/doctorExam";

// Hủy chỉ định chưa có kết quả, bắt buộc lý do; chỉ định đã hủy không hoàn tiền (R3 UC45 AF2, BR-53).

const CAUSES: Record<CancelCause, string> = {
  PATIENT_DECLINED: "Bệnh nhân từ chối",
  CLINIC_UNABLE:
    "Phòng khám không thực hiện được (chống chỉ định, thay chỉ định...)",
};

export function CancelOrderDialog({
  order,
  onClose,
  onCancelled,
}: {
  order: ParaclinicalOrder | null;
  onClose: () => void;
  onCancelled: () => void;
}) {
  const [cause, setCause] = useState<CancelCause>("CLINIC_UNABLE");
  const [reason, setReason] = useState("");
  const { pending, run } = useAsyncAction();
  const empty = !reason.trim();

  async function confirm() {
    if (!order || empty) return;
    const done = await run(async () => {
      await getDoctorExamApi().cancelOrder(order, cause, reason.trim());
      return true;
    }, "MSG-46");
    if (!done) return;
    setReason("");
    onCancelled();
    onClose();
  }

  return (
    <Dialog open={order !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Hủy chỉ định</DialogTitle>
          <DialogDescription>
            {order?.serviceName}. Chỉ định đã hủy không được hoàn tiền, kể cả
            khi đã thanh toán.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="cancel-cause">Nguyên nhân</Label>
            <NativeSelect
              id="cancel-cause"
              className="w-full"
              value={cause}
              onChange={(e) => setCause(e.target.value as CancelCause)}
            >
              {(Object.keys(CAUSES) as CancelCause[]).map((item) => (
                <NativeSelectOption key={item} value={item}>
                  {CAUSES[item]}
                </NativeSelectOption>
              ))}
            </NativeSelect>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="cancel-reason">Lý do hủy</Label>
            <Textarea
              id="cancel-reason"
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </div>
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose}>
            Giữ chỉ định
          </Button>
          <Button
            type="button"
            variant="destructive"
            disabled={pending || empty}
            onClick={() => void confirm()}
          >
            {pending ? "Đang hủy..." : "Hủy chỉ định"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
