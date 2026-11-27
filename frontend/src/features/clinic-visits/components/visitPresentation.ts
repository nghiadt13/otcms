import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { formatDate } from "@/lib/format/formatDate";
import type {
  QueueTicket,
  VisitPatient,
  VisitProgress,
} from "../types/visitProgress";

// Chữ hiển thị của lượt khám khu bệnh nhân. Không có luật nghiệp vụ: chỉ đổi dữ liệu thành chữ.

const WEEKDAY_LABELS = [
  "Chủ nhật",
  "Thứ hai",
  "Thứ ba",
  "Thứ tư",
  "Thứ năm",
  "Thứ sáu",
  "Thứ bảy",
] as const;

/** "Thứ sáu 23/10/2026" từ yyyy-MM-dd; tính thứ theo ngày, không đổi múi giờ máy. */
export function visitDateLabel(isoDate: string): string {
  const weekday = WEEKDAY_LABELS[new Date(`${isoDate}T00:00:00Z`).getUTCDay()];
  return `${weekday} ${formatDate(isoDate)}`;
}

/** "Nguyễn Văn An · Bản thân": ghi người khám và quan hệ; liên kết cũ chưa có quan hệ thì chỉ ghi tên. */
export const visitPatientLabel = (patient: VisitPatient): string =>
  patient.relationship
    ? `${patient.fullName} · ${CARE_RELATIONSHIP_LABELS[patient.relationship]}`
    : patient.fullName;

/**
 * Câu nói bệnh nhân đang đứng ở đâu trong hàng: đang được gọi, là người kế tiếp, hoặc còn bao nhiêu
 * người trước. Lượt chưa vào hàng (không có số người trước) thì trả chuỗi rỗng.
 */
export function queueAheadText(
  progress: Pick<VisitProgress, "beingCalled" | "peopleAhead">,
): string {
  if (progress.beingCalled) return "Đang gọi bạn";
  if (progress.peopleAhead === null) return "";
  return progress.peopleAhead === 0
    ? "Bạn là người tiếp theo."
    : `Còn ${progress.peopleAhead} người trước bạn.`;
}

/** Hình thức vào khám: đặt lịch trước hay lễ tân tiếp nhận trực tiếp (vãng lai). */
export const visitSourceLabel = (source: VisitProgress["source"]): string =>
  source === "WALK_IN" ? "Khám tại quầy" : "Đặt lịch trước";

/** Phiếu số của hàng đang chờ gần nhất (thẻ tóm tắt ở trang chủ); null khi lượt không ở hàng phòng. */
export function ticketOfProgress(progress: VisitProgress): QueueTicket | null {
  if (progress.queueNumber === null) return null;
  return {
    queueNumber: progress.queueNumber,
    peopleAhead: progress.peopleAhead,
    beingCalled: progress.beingCalled,
    room: progress.currentRoom,
    stage: progress.queueStage,
  };
}
