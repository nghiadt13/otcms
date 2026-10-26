import { StatusBadge } from "@/components/admin/StatusBadge";
import {
  APPOINTMENT_STATUS_LABELS,
  APPOINTMENT_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
import type { AppointmentStatus } from "../types/appointment";

// Nhãn và tone lấy từ lib/domain/clinicVisit để lịch hẹn ở admin và lễ tân hiển thị giống nhau.
export function AppointmentStatusBadge({
  status,
}: {
  status: AppointmentStatus;
}) {
  return (
    <StatusBadge tone={APPOINTMENT_STATUS_TONE[status]}>
      {APPOINTMENT_STATUS_LABELS[status]}
    </StatusBadge>
  );
}
