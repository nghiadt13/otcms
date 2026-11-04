"use client";

import { CircleCheck } from "lucide-react";
import { useId, type ReactNode } from "react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import {
  APPOINTMENT_STATUS_LABELS,
  APPOINTMENT_STATUS_TONE,
  EXAM_FEE_STATUS_LABELS,
  EXAM_FEE_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { longStartLabel } from "../schema/bookingTime";
import type { BookedAppointment, BookingVariant } from "../types/booking";
import { bookingSize } from "./booking/bookingSizes";

// Kết quả sau khi đặt: thay toàn bộ luồng. Ba khối: banner xác nhận, thông tin lịch hẹn, phí khám.
// `actions` do trang truyền vào (ví dụ "Đặt lịch khác").
// Không có dòng "email xác nhận": BE gửi email tùy người có email, FE không khẳng định thay BE.

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid gap-0.5 py-3 sm:grid-cols-[8rem_1fr] sm:gap-4">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="min-w-0 break-words font-medium">{value}</dd>
    </div>
  );
}

export function BookingResult({
  appointment,
  actions,
  variant = "patient",
}: {
  appointment: BookedAppointment;
  actions?: ReactNode;
  variant?: BookingVariant;
}) {
  const sizes = bookingSize(variant);
  const headingId = useId();
  const { status, examFee } = appointment;

  return (
    <section aria-labelledby={headingId} className={`grid gap-4 ${sizes.body}`}>
      <div className="flex flex-wrap items-center gap-4 rounded-xl border border-primary/30 bg-primary/5 p-5">
        <CircleCheck aria-hidden className="size-10 shrink-0 text-primary" />
        <div className="min-w-0 flex-1">
          <h2 id={headingId} className="text-xl font-semibold">
            Đã đặt lịch khám
          </h2>
          <p className="text-muted-foreground break-words">
            {appointment.patient.fullName} · {appointment.service.name}
          </p>
        </div>
        <StatusBadge tone={APPOINTMENT_STATUS_TONE[status]}>
          {APPOINTMENT_STATUS_LABELS[status]}
        </StatusBadge>
      </div>

      <div className="grid items-start gap-4 md:grid-cols-[1.6fr_1fr]">
        <section
          aria-label="Thông tin lịch hẹn"
          className="rounded-xl border bg-card"
        >
          <h3 className="border-b px-5 py-3 font-semibold">
            Thông tin lịch hẹn
          </h3>
          <dl className="divide-y px-5">
            <InfoRow label="Người khám" value={appointment.patient.fullName} />
            <InfoRow label="Dịch vụ" value={appointment.service.name} />
            <InfoRow
              label="Thời gian"
              value={longStartLabel(appointment.startAt)}
            />
            <InfoRow label="Bác sĩ" value={appointment.doctor.fullName} />
            <InfoRow label="Phòng khám" value={appointment.roomName} />
          </dl>
        </section>

        <section aria-label="Phí khám" className="rounded-xl border bg-card">
          <h3 className="border-b px-5 py-3 font-semibold">Phí khám</h3>
          <div className="grid gap-3 px-5 py-4">
            <p className="text-2xl font-semibold tabular-nums">
              {formatCurrencyVnd(examFee.amount)}
            </p>
            <div>
              <StatusBadge tone={EXAM_FEE_STATUS_TONE[examFee.status]}>
                {EXAM_FEE_STATUS_LABELS[examFee.status]}
              </StatusBadge>
            </div>
            {examFee.status === "PENDING_PAYMENT" ? (
              <p className="text-sm text-muted-foreground">
                Phí khám cần được thanh toán trước khi check-in. Bạn có thể
                thanh toán online hoặc trả tại quầy.
              </p>
            ) : null}
          </div>
        </section>
      </div>

      {actions ? <div className="flex flex-wrap gap-3">{actions}</div> : null}
    </section>
  );
}
