"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { todayIsoDate, formatDateTime } from "@/lib/format/formatDate";
import { messageFromError } from "@/lib/messages/appMessages";
import { notifyError, notifySuccess } from "@/lib/messages/notify";

export type RescheduleSlot = { doctorId: string; doctorDisplayName: string; startTime: string };
export function RescheduleAppointmentDialog({ open, onClose, loadSlots, onConfirm, currentDoctorId }: {
  open: boolean; onClose: () => void;
  /** R3 has a success toast only for doctor reassignment (MSG-27); a time-only change shows none yet. */
  currentDoctorId?: string | null;
  loadSlots: (date: string, signal: AbortSignal) => Promise<RescheduleSlot[]>;
  onConfirm: (slot: RescheduleSlot, reason: string) => Promise<void>;
}) {
  const [date, setDate] = useState(todayIsoDate());
  const [slots, setSlots] = useState<RescheduleSlot[] | null>(null);
  const [selection, setSelection] = useState("");
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  useEffect(() => {
    if (!open || !date) return;
    const controller = new AbortController();
    loadSlots(date, controller.signal).then((items) => { setSlots(items); setError(""); })
      .catch((cause: unknown) => { if (!controller.signal.aborted) setError(messageFromError(cause)); });
    return () => controller.abort();
  }, [open, date, loadSlots]);
  async function submit() {
    const slot = slots?.find((item) => `${item.doctorId}|${item.startTime}` === selection);
    if (!slot || !reason.trim()) return;
    setPending(true);
    try { await onConfirm(slot, reason.trim()); if (slot.doctorId !== currentDoctorId) notifySuccess("MSG-27", { doctor: slot.doctorDisplayName }); setReason(""); onClose(); }
    catch (cause) { notifyError(cause); }
    finally { setPending(false); }
  }
  return <Dialog open={open} onOpenChange={(value) => { if (!value && !pending) onClose(); }}><DialogContent>
    <DialogHeader><DialogTitle>Đổi giờ hẹn</DialogTitle><DialogDescription>Chọn giờ và bác sĩ còn trống, rồi nhập lý do.</DialogDescription></DialogHeader>
    <label className="grid gap-2">Ngày khám<Input type="date" min={todayIsoDate()} value={date} onChange={(e) => { setDate(e.target.value); setSelection(""); setSlots(null); }} /></label>
    {error ? <p role="alert">{error}</p> : !slots ? <p>Đang tải...</p> : slots.length === 0 ? <p>Không có giờ trống.</p> : <label className="grid gap-2">Giờ và bác sĩ<select className="rounded-md border p-2" value={selection} onChange={(e) => setSelection(e.target.value)}><option value="">Chọn giờ và bác sĩ</option>{slots.map((slot) => <option key={`${slot.doctorId}|${slot.startTime}`} value={`${slot.doctorId}|${slot.startTime}`}>{formatDateTime(slot.startTime)} · {slot.doctorDisplayName}</option>)}</select></label>}
    <label className="grid gap-2">Lý do đổi giờ<textarea className="min-h-24 rounded-md border p-2" maxLength={1000} value={reason} onChange={(e) => setReason(e.target.value)} /></label>
    <DialogFooter><Button variant="outline" disabled={pending} onClick={onClose}>Đóng</Button><Button disabled={pending || !selection || !reason.trim()} onClick={() => void submit()}>Xác nhận</Button></DialogFooter>
  </DialogContent></Dialog>;
}
