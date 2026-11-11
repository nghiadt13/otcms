"use client";

import { useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import {
  createAppointmentInvoice,
  type BillingInvoice,
} from "@/lib/api/billingApi";
import { messageFromError } from "@/lib/messages/appMessages";
import {
  checkInAppointment,
  recordManualReceptionArrival,
  type ReceptionAppointment,
  type ReceptionArrival,
  type ReceptionCheckIn,
} from "../api/receptionLiveApi";
import { paymentErrorText } from "../schema/receptionMessages";
import { CounterPaymentForm } from "./CounterPaymentForm";
import { timeText } from "./ReceptionUi";

const feeText = (paid: boolean | null) =>
  paid === true
    ? "đã thanh toán"
    : paid === false
      ? "chưa thanh toán"
      : "chưa rõ";

/**
 * Một lịch hẹn trong hộp thoại tiếp nhận. Phí khám chưa trả thì thu tại quầy trước; nút check-in chỉ
 * bật khi không còn phí phải thu. Ghi nhận đến thủ công là tùy chọn, không còn là điều kiện check-in.
 */
export function CheckInAppointmentRow({
  appointment,
  arrival,
  verifiedPatientProfileId,
  onCheckedIn,
  onArrivalRecorded,
  onFeePaid,
}: {
  appointment: ReceptionAppointment;
  arrival: ReceptionArrival | undefined;
  verifiedPatientProfileId: string;
  onCheckedIn: (result: ReceptionCheckIn) => void;
  onArrivalRecorded: (arrival: ReceptionArrival) => void;
  onFeePaid: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [invoice, setInvoice] = useState<BillingInvoice | null>(null);
  const [arrivalReason, setArrivalReason] = useState("");
  // Giữ khoá idempotency qua lần thử lại sau lỗi mạng; thành công thì bỏ.
  const checkInKey = useRef<string | null>(null);
  const feeUnpaid = appointment.examFeePaid === false;

  if (appointment.status === "NO_SHOW")
    return (
      <p className="text-sm text-muted-foreground">
        Lịch hẹn đã chuyển sang không đến; hãy tạo lượt walk-in.
      </p>
    );

  async function openFeeStep() {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      setInvoice(await createAppointmentInvoice(appointment.id));
    } catch (cause) {
      setError(paymentErrorText(cause));
    } finally {
      setBusy(false);
    }
  }

  async function checkIn() {
    if (busy || feeUnpaid) return;
    setBusy(true);
    setError("");
    checkInKey.current ??= crypto.randomUUID();
    try {
      const result = await checkInAppointment({
        appointmentId: appointment.id,
        verifiedPatientProfileId,
        idempotencyKey: checkInKey.current,
      });
      checkInKey.current = null;
      onCheckedIn(result);
    } catch (cause) {
      setError(messageFromError(cause));
    } finally {
      setBusy(false);
    }
  }

  async function recordArrival() {
    if (busy || arrivalReason.trim().length === 0) return;
    setBusy(true);
    setError("");
    try {
      const recorded = await recordManualReceptionArrival({
        appointmentId: appointment.id,
        reason: arrivalReason,
      });
      setArrivalReason("");
      onArrivalRecorded(recorded);
    } catch (cause) {
      setError(messageFromError(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-3 rounded-md border p-3">
      <div>
        <p className="font-medium">
          {appointment.serviceName} · {timeText(appointment.startTime)}
        </p>
        <p className="text-sm text-muted-foreground">
          {appointment.doctorDisplayName ?? "Chưa phân bác sĩ"} · Phí khám:{" "}
          {feeText(appointment.examFeePaid)}
        </p>
      </div>
      {feeUnpaid && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-md bg-muted p-3">
          <p className="text-sm">Thu phí khám tại quầy trước khi tiếp nhận.</p>
          <Button
            type="button"
            variant="outline"
            disabled={busy}
            onClick={() => void openFeeStep()}
          >
            Thu phí khám
          </Button>
        </div>
      )}
      <div>
        <Button
          type="button"
          disabled={busy || feeUnpaid}
          onClick={() => void checkIn()}
        >
          Check-in lịch hẹn
        </Button>
      </div>
      {!arrival && (
        <div className="space-y-2">
          <p className="text-sm text-muted-foreground">
            Chưa có ghi nhận bệnh nhân đến bằng QR. Có thể ghi nhận thủ công.
          </p>
          <Textarea
            aria-label="Lý do xác nhận bệnh nhân đã đến"
            value={arrivalReason}
            onChange={(event) => setArrivalReason(event.target.value)}
            placeholder="Lý do ghi nhận thủ công"
            maxLength={500}
          />
          <Button
            type="button"
            variant="outline"
            disabled={busy || arrivalReason.trim().length === 0}
            onClick={() => void recordArrival()}
          >
            Xác nhận bệnh nhân đã đến
          </Button>
        </div>
      )}
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
      {invoice && (
        <CounterPaymentForm
          invoice={invoice}
          title="Thu phí khám"
          description="Ghi nhận phí khám của lịch hẹn trên hóa đơn."
          onClose={() => setInvoice(null)}
          onPaid={async () => {
            setInvoice(null);
            onFeePaid();
          }}
        />
      )}
    </div>
  );
}
