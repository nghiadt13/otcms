import Link from "next/link";
import { ChevronRight } from "lucide-react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import {
  APPOINTMENT_STATUS_LABELS,
  APPOINTMENT_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import type { PatientAppointment } from "../../types/patientAppointment";
import {
  appointmentWhenLabel,
  examFeeLabel,
  patientOfLabel,
} from "./patientAppointmentPresentation";

// Một dòng trong danh sách lịch hẹn: cả dòng là link tới chi tiết. Ghi đủ giờ, dịch vụ, bác sĩ,
// "của ai", trạng thái và phí, để đọc được mà không cần mở chi tiết.
export function PatientAppointmentRow({
  appointment,
}: {
  appointment: PatientAppointment;
}) {
  const { status, examFee } = appointment;
  return (
    <li>
      <Link
        className="flex min-h-12 items-center gap-3 px-4 py-4 transition-colors hover:bg-muted/50 focus-visible:bg-muted/50 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring/50 motion-reduce:transition-none"
        href={PATIENT_ROUTES.appointmentDetail(appointment.id)}
      >
        <span className="grid min-w-0 flex-1 gap-1 text-base">
          <span className="font-semibold tabular-nums">
            {appointmentWhenLabel(appointment.startAt)}
          </span>
          <span className="break-words">{appointment.service.name}</span>
          <span className="break-words text-muted-foreground">
            {appointment.doctor.fullName} · {appointment.roomName}
          </span>
          <span className="break-words font-medium">
            {patientOfLabel(appointment.patient)}
          </span>
          <span className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1">
            <StatusBadge tone={APPOINTMENT_STATUS_TONE[status]}>
              {APPOINTMENT_STATUS_LABELS[status]}
            </StatusBadge>
            <span className="tabular-nums">{examFeeLabel(examFee)}</span>
          </span>
        </span>
        <ChevronRight
          aria-hidden
          className="size-5 shrink-0 text-muted-foreground"
        />
      </Link>
    </li>
  );
}
