import { cn } from "@/lib/utils";
import type { PatientAppointment } from "../../types/patientAppointment";
import {
  appointmentWhenLabel,
  examFeeLabel,
  patientOfLabel,
  visitTypeLabel,
} from "./patientAppointmentPresentation";

// Các dòng thông tin của một lịch, nhãn và giá trị cỡ chữ khu bệnh nhân (text-base). Người khám
// luôn ghi kèm quan hệ để không nhầm người.
export function PatientAppointmentFacts({
  appointment,
}: {
  appointment: PatientAppointment;
}) {
  const items: { label: string; value: string; numeric?: boolean }[] = [
    { label: "Giờ khám", value: appointmentWhenLabel(appointment.startAt), numeric: true },
    { label: "Người khám", value: patientOfLabel(appointment.patient) },
    { label: "Dịch vụ", value: appointment.service.name },
    { label: "Bác sĩ", value: appointment.doctor.fullName },
    { label: "Phòng", value: appointment.roomName },
    { label: "Phí khám", value: examFeeLabel(appointment.examFee), numeric: true },
    { label: "Loại", value: visitTypeLabel(appointment.isFollowUp) },
  ];
  return (
    <dl className="grid gap-x-6 gap-y-5 rounded-lg border bg-card px-4 py-5 text-base sm:grid-cols-2">
      {items.map(({ label, value, numeric }) => (
        <div className="min-w-0" key={label}>
          <dt className="text-base text-muted-foreground">{label}</dt>
          <dd className={cn("mt-1 break-words font-medium", numeric && "tabular-nums")}>
            {value}
          </dd>
        </div>
      ))}
    </dl>
  );
}
