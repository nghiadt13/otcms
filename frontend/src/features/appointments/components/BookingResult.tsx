"use client";

import { useId, type ReactNode } from "react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import {
  APPOINTMENT_STATUS_LABELS,
  APPOINTMENT_STATUS_TONE,
  EXAM_FEE_STATUS_LABELS,
} from "@/lib/domain/clinicVisit";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { longStartLabel } from "../schema/bookingTime";
import type { BookedAppointment, BookingVariant } from "../types/booking";
import { bookingSize } from "./booking/bookingSizes";

// Kết quả sau khi đặt: thay toàn bộ luồng. `actions` do trang truyền vào (ví dụ "Đặt lịch khác").
// Không có dòng "email xác nhận": BE gửi email tùy người có email, FE không khẳng định thay BE.

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
    <section aria-labelledby={headingId} className="grid gap-6">
      <div className="grid gap-3">
        <h2 id={headingId} className={sizes.title}>
          Đã đặt lịch khám
        </h2>
        <div className={`grid gap-1.5 ${sizes.body}`}>
          <p className="font-medium break-words">
            {appointment.patient.fullName} · {appointment.service.name}
          </p>
          <p className="break-words">
            {longStartLabel(appointment.startAt)} · {appointment.doctor.fullName}{" "}
            · {appointment.roomName}
          </p>
          <p>
            Phí khám{" "}
            <span className="tabular-nums">{formatCurrencyVnd(examFee.amount)}</span>{" "}
            · {EXAM_FEE_STATUS_LABELS[examFee.status]}
          </p>
        </div>
        <div>
          <StatusBadge tone={APPOINTMENT_STATUS_TONE[status]}>
            {APPOINTMENT_STATUS_LABELS[status]}
          </StatusBadge>
        </div>
      </div>
      {actions ? <div className="flex flex-wrap gap-3">{actions}</div> : null}
    </section>
  );
}
