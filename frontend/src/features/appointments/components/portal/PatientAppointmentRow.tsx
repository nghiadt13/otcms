import { Stethoscope, UserRound } from "lucide-react";
import Link from "next/link";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import {
  APPOINTMENT_STATUS_LABELS,
  APPOINTMENT_STATUS_TONE,
  EXAM_FEE_STATUS_LABELS,
} from "@/lib/domain/clinicVisit";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatTime } from "@/lib/format/formatDate";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import type { PatientAppointment } from "../../types/patientAppointment";
import {
  appointmentDateParts,
  patientOfLabel,
  splitServiceName,
} from "./patientAppointmentPresentation";

// Thẻ một lịch hẹn, mỗi thẻ một hàng. Từ trên xuống: trạng thái; ô ngày, giờ và dịch vụ; bác sĩ và
// người khám; phí khám cùng nút "Chi tiết lịch khám" (link duy nhất của thẻ, tới trang chi tiết).
export function PatientAppointmentRow({
  appointment,
}: {
  appointment: PatientAppointment;
}) {
  const { status, examFee } = appointment;
  const date = appointmentDateParts(appointment.startAt);
  const service = splitServiceName(appointment.service.name);

  return (
    <li>
      <article className="flex flex-col gap-4 rounded-xl border bg-card p-5 sm:p-6">
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge tone={APPOINTMENT_STATUS_TONE[status]}>
            {APPOINTMENT_STATUS_LABELS[status]}
          </StatusBadge>
          {appointment.isFollowUp ? (
            <StatusBadge tone="neutral">Tái khám</StatusBadge>
          ) : null}
          {appointment.doctorUnavailable ? (
            <StatusBadge tone="warning">Bác sĩ vắng</StatusBadge>
          ) : null}
        </div>

        <div className="flex items-start gap-4">
          <div
            aria-hidden
            className="flex w-16 shrink-0 flex-col items-center rounded-lg bg-muted/70 px-2 py-2 text-center"
          >
            <span className="text-[0.6875rem] font-semibold tracking-wide text-muted-foreground uppercase">
              Tháng {date.month}
            </span>
            <span className="text-2xl leading-tight font-semibold text-primary tabular-nums">
              {date.day}
            </span>
            <span className="text-xs text-muted-foreground">
              {date.weekday}
            </span>
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-xl font-semibold tabular-nums">
              <span className="sr-only">
                {`${date.weekday}, ngày ${date.day} tháng ${date.month}, lúc `}
              </span>
              {formatTime(appointment.startAt)}
            </p>
            <h3 className="mt-1 text-base font-semibold break-words">
              {service.title}
            </h3>
            {service.subtitle ? (
              <p className="text-sm break-words text-muted-foreground">
                {service.subtitle}
              </p>
            ) : null}
          </div>
        </div>

        <ul className="grid gap-2.5 border-t pt-4 text-base">
          <li className="flex items-center gap-3">
            <Stethoscope
              aria-hidden
              className="size-5 shrink-0 text-muted-foreground"
            />
            <span className="break-words">
              {appointment.doctor.fullName}
              <span className="text-muted-foreground">
                {" "}
                · {appointment.roomName}
              </span>
            </span>
          </li>
          <li className="flex items-center gap-3">
            <UserRound
              aria-hidden
              className="size-5 shrink-0 text-muted-foreground"
            />
            <span className="font-medium break-words">
              {patientOfLabel(appointment.patient)}
            </span>
          </li>
        </ul>

        <div className="flex flex-col gap-4 border-t pt-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="text-sm text-muted-foreground">Chi phí khám</p>
            <p className="text-xl font-semibold tabular-nums">
              {formatCurrencyVnd(examFee.amount)}
            </p>
            <p
              className={cn(
                "text-sm font-medium",
                examFee.status === "PAID" && "text-success",
                examFee.status === "PENDING_PAYMENT" && "text-warning",
                examFee.status === "NOT_REQUIRED" && "text-muted-foreground",
              )}
            >
              {EXAM_FEE_STATUS_LABELS[examFee.status]}
            </p>
          </div>
          <Link
            className={cn(publicButtonClass("primary"), "w-full sm:w-auto")}
            href={PATIENT_ROUTES.appointmentDetail(appointment.id)}
          >
            Chi tiết lịch khám
          </Link>
        </div>
      </article>
    </li>
  );
}
