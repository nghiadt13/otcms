import { StatusBadge, type StatusTone } from "@/components/admin/StatusBadge";
import {
  SERVICE_ORDER_STATUS_LABELS,
  SERVICE_ORDER_STATUS_TONE,
  VISIT_STATUS_LABELS,
  VISIT_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
import type { ParaclinicalOrder, TodayStatus } from "../types/doctorExam";

// Nhãn trạng thái dùng chung trong khu bác sĩ; chữ lấy từ lib/domain/clinicVisit để các khu
// hiển thị cùng một từ cho cùng một trạng thái.

export const TODAY_STATUS_LABELS: Record<TodayStatus, string> = {
  ...VISIT_STATUS_LABELS,
  NOT_ARRIVED: "Chưa đến",
};

const TODAY_STATUS_TONE: Record<TodayStatus, StatusTone> = {
  ...VISIT_STATUS_TONE,
  NOT_ARRIVED: "neutral",
};

export function TodayStatusBadge({ status }: { status: TodayStatus }) {
  return (
    <StatusBadge tone={TODAY_STATUS_TONE[status]}>
      {TODAY_STATUS_LABELS[status]}
    </StatusBadge>
  );
}

export function OrderStatusBadge({ order }: { order: ParaclinicalOrder }) {
  return (
    <StatusBadge tone={SERVICE_ORDER_STATUS_TONE[order.status]}>
      {SERVICE_ORDER_STATUS_LABELS[order.status]}
    </StatusBadge>
  );
}

/** Lượt khám đã xong hẳn: không còn ghi nhận khám, chỉ định hay đơn thuốc. */
export function isVisitClosed(status: TodayStatus | null): boolean {
  return status === "COMPLETED" || status === "INCOMPLETE";
}
