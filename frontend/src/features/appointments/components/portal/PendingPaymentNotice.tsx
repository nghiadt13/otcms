"use client";

import { Clock } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import { listAwaitingPaymentAppointments } from "../../api/patientAppointmentSource";
import { longStartLabel } from "../../schema/bookingTime";
import type { PatientAppointment } from "../../types/patientAppointment";
import { PatientCancelDialog } from "./PatientCancelDialog";

// Nhắc ở màn đặt lịch: lịch tự đặt mà chưa thanh toán xong không hiện trong "Lịch hẹn" (lịch chỉ
// được xác nhận sau khi trả phí) nhưng vẫn giữ giờ khám. Người dùng thanh toán tiếp hoặc hủy để nhả
// giờ. Chỉ đọc một lần khi mở màn, trước khi wizard tạo lịch mới; lỗi tải thì im lặng (không chặn đặt lịch).

export function PendingPaymentNotice() {
  const [items, setItems] = useState<PatientAppointment[]>([]);
  const [cancelling, setCancelling] = useState<PatientAppointment | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    listAwaitingPaymentAppointments(controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setItems(result);
      })
      .catch(() => undefined);
    return () => controller.abort();
  }, []);

  const remove = (id: string) =>
    setItems((current) => current.filter((item) => item.id !== id));

  if (items.length === 0) return null;
  return (
    <section
      aria-labelledby="pending-payment-heading"
      className="grid gap-3 rounded-xl border border-coral/40 bg-coral-soft p-4 sm:p-5"
    >
      <h2
        id="pending-payment-heading"
        className="flex items-center gap-2 text-base font-semibold text-coral-strong"
      >
        <Clock aria-hidden className="size-5 shrink-0" />
        Bạn có lịch khám đang chờ thanh toán
      </h2>
      <p className="text-sm text-coral-ink">
        Lịch chỉ được xác nhận sau khi thanh toán thành công. Hoàn tất thanh
        toán hoặc hủy lịch để nhả giờ khám.
      </p>
      <ul className="grid gap-3">
        {items.map((item) => (
          <li
            key={item.id}
            className="grid gap-3 rounded-lg bg-card p-3 sm:grid-cols-[1fr_auto] sm:items-center"
          >
            <div className="min-w-0">
              <p className="break-words text-base font-semibold">
                {item.service.name} · {item.patient.fullName}
              </p>
              <p className="text-sm text-muted-foreground">
                {longStartLabel(item.startAt)}
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Link
                className={publicButtonClass("primary")}
                href={PATIENT_ROUTES.payAppointment(item.id)}
              >
                Thanh toán tiếp
              </Link>
              <button
                className={cn(publicButtonClass("secondary"))}
                onClick={() => setCancelling(item)}
                type="button"
              >
                Hủy lịch
              </button>
            </div>
          </li>
        ))}
      </ul>
      {cancelling ? (
        <PatientCancelDialog
          appointment={cancelling}
          onCancelled={() => remove(cancelling.id)}
          onOpenChange={(open) => {
            if (!open) setCancelling(null);
          }}
          onReload={() => remove(cancelling.id)}
          open
        />
      ) : null}
    </section>
  );
}
