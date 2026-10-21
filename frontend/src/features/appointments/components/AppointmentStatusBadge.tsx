import { StatusBadge, type StatusTone } from "@/components/admin/StatusBadge";
import type { AppointmentStatus } from "../types/appointment";

export const STATUS_LABELS: Record<AppointmentStatus, string> = {
  BOOKED: "Đã đặt",
  CHECKED_IN: "Đã check-in",
  CANCELLED: "Đã hủy",
  NO_SHOW: "Không đến khám",
};

const STATUS_TONES: Record<AppointmentStatus, StatusTone> = {
  BOOKED: "info",
  CHECKED_IN: "success",
  CANCELLED: "neutral",
  NO_SHOW: "warning",
};

export function AppointmentStatusBadge({
  status,
}: {
  status: AppointmentStatus;
}) {
  return (
    <StatusBadge tone={STATUS_TONES[status]}>
      {STATUS_LABELS[status]}
    </StatusBadge>
  );
}
