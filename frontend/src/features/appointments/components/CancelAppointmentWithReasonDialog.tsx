"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { APP_MESSAGES } from "@/lib/messages/appMessages";
import { notifyError, notifySuccess } from "@/lib/messages/notify";

export function CancelAppointmentWithReasonDialog({ open, onClose, onConfirm }: {
  open: boolean; onClose: () => void; onConfirm: (reason: string) => Promise<void>;
}) {
  const [reason, setReason] = useState("");
  const [pending, setPending] = useState(false);
  async function submit() {
    if (!reason.trim()) return;
    setPending(true);
    try { await onConfirm(reason.trim()); notifySuccess("MSG-28"); setReason(""); onClose(); }
    catch (error) { notifyError(error); }
    finally { setPending(false); }
  }
  return <Dialog open={open} onOpenChange={(value) => { if (!value && !pending) onClose(); }}>
    <DialogContent><DialogHeader><DialogTitle>Hủy lịch hẹn</DialogTitle><DialogDescription>{APP_MESSAGES["MSG-93"]}</DialogDescription></DialogHeader>
      <label className="grid gap-2">Lý do hủy <textarea className="min-h-24 rounded-md border p-2" maxLength={1000} value={reason} onChange={(e) => setReason(e.target.value)} /></label>
      <DialogFooter><Button variant="outline" disabled={pending} onClick={onClose}>Đóng</Button><Button disabled={pending || !reason.trim()} onClick={() => void submit()}>Xác nhận hủy</Button></DialogFooter>
    </DialogContent>
  </Dialog>;
}
