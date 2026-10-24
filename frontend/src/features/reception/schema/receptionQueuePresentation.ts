import { QUEUE_STATUS_LABELS, VISIT_TYPE_LABELS } from "@/lib/domain/clinicVisit";
import { formatTime } from "@/lib/format/formatDate";
import type { ReceptionQueueType, ReceptionVisitSummary } from "../types/receptionQueue";

// Chữ hiển thị của màn Hàng đợi tiếp nhận: nhãn ngắn cho cột, dòng trong Sheet, dòng cập nhật.
// Hàm thuần, không gọi API. Câu chữ không có mã UC/BR/MSG.

/** Tên hàng đợi trong bảng (ngắn để vừa cột). */
export const QUEUE_TYPE_SHORT_LABELS: Record<ReceptionQueueType, string> = {
  EXAMINATION: "Khám",
  RESULT_REVIEW: "Đọc kết quả",
};

/** Cột Lịch hẹn: giờ hẹn, hoặc "Vãng lai" khi không có lịch (vãng lai hoặc quay lại). */
export function appointmentCell(startAt: string | null): string {
  return startAt ? formatTime(startAt) : "Vãng lai";
}

/** "1 người phía trước"; null khi bản ghi không ở hàng Đang chờ. */
export function peopleAheadPhrase(count: number | null): string | null {
  if (count === null) return null;
  return count === 0 ? "Không có ai phía trước" : `${count} người phía trước`;
}

/** Dòng "Hàng đợi" trong Sheet: loại · trạng thái · người phía trước (nếu đang chờ). */
export function summaryQueueLine(
  summary: Pick<ReceptionVisitSummary, "queueType" | "queueStatus" | "peopleAhead">,
): string {
  const parts = [
    QUEUE_TYPE_SHORT_LABELS[summary.queueType],
    QUEUE_STATUS_LABELS[summary.queueStatus],
    peopleAheadPhrase(summary.peopleAhead),
  ];
  return parts.filter((part): part is string => part !== null).join(" · ");
}

/** Dòng "Tiếp nhận lúc" trong Sheet: giờ tiếp nhận · lịch hẹn hoặc Vãng lai · loại lượt khám. */
export function summaryCheckInLine(
  summary: Pick<ReceptionVisitSummary, "checkedInAt" | "appointmentStartAt" | "visitType">,
): string {
  const schedule = summary.appointmentStartAt
    ? `Lịch hẹn ${formatTime(summary.appointmentStartAt)}`
    : "Vãng lai";
  return `${formatTime(summary.checkedInAt)} · ${schedule} · ${VISIT_TYPE_LABELS[summary.visitType]}`;
}

/** Chữ cập nhật cạnh nút Tải lại, theo giờ BE trả (generatedAt). */
export function refreshedAtText(generatedAt: string): string {
  return `Cập nhật ${formatTime(generatedAt)}`;
}
