import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import type { VisitStatus } from "@/lib/domain/clinicVisit";
import { formatDate } from "@/lib/format/formatDate";
import type {
  VisitPatient,
  VisitProgress,
  VisitProgressOrder,
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

/** Dòng phòng của chỉ định: tên phòng khi BE đã trả; chưa có tên thì chỉ nói đã có hay chưa có phòng. */
export function orderRoomText(
  order: Pick<VisitProgressOrder, "roomId" | "roomName">,
): string {
  if (order.roomName) return `Phòng thực hiện: ${order.roomName}`;
  return order.roomId ? "Đã có phòng thực hiện" : "Chưa có phòng thực hiện";
}

/** Hình thức vào khám: đặt lịch trước hay lễ tân tiếp nhận trực tiếp (vãng lai). */
export const visitSourceLabel = (source: VisitProgress["source"]): string =>
  source === "WALK_IN" ? "Khám tại quầy" : "Đặt lịch trước";

/** Các chặng của một lượt khám, theo thứ tự diễn ra. */
export const VISIT_STEP_LABELS = [
  "Tiếp nhận",
  "Chờ khám",
  "Đang khám",
  "Chờ kết luận",
] as const;

/**
 * Chặng đang ở (0-based trong VISIT_STEP_LABELS); bằng độ dài danh sách khi lượt đã hoàn tất.
 * Lượt dở dang hoặc đã rời đi không có chặng, trả null để màn không vẽ thanh tiến độ.
 */
export function visitStepIndex(status: VisitStatus): number | null {
  switch (status) {
    case "WAITING_FOR_EXAMINATION":
      return 1;
    case "IN_EXAMINATION":
      return 2;
    case "AWAITING_CONCLUSION":
      return 3;
    case "COMPLETED":
      return VISIT_STEP_LABELS.length;
    default:
      return null;
  }
}
