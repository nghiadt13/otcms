import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { formatDate } from "@/lib/format/formatDate";
import type {
  ServiceKind,
  VisitOrderProgress,
  VisitPatient,
  VisitQueuePosition,
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

const SERVICE_KIND_LABELS: Record<ServiceKind, string> = {
  IMAGING: "Chẩn đoán hình ảnh",
  LABORATORY: "Xét nghiệm",
};

/** "Thứ sáu 23/10/2026" từ yyyy-MM-dd; tính thứ theo ngày, không đổi múi giờ máy. */
export function visitDateLabel(isoDate: string): string {
  const weekday = WEEKDAY_LABELS[new Date(`${isoDate}T00:00:00Z`).getUTCDay()];
  return `${weekday} ${formatDate(isoDate)}`;
}

/** "Nguyễn Văn An · Bản thân": luôn ghi người khám và quan hệ. */
export const visitPatientLabel = (patient: VisitPatient): string =>
  `${patient.fullName} · ${CARE_RELATIONSHIP_LABELS[patient.relationship]}`;

/** "Chẩn đoán hình ảnh · Phòng X-quang 1"; phòng bỏ qua khi chưa có. */
export function orderPlaceLabel(order: VisitOrderProgress): string {
  return [SERVICE_KIND_LABELS[order.kind], order.roomName]
    .filter(Boolean)
    .join(" · ");
}

/**
 * Câu nói bệnh nhân đang đứng ở đâu trong hàng. Chữ thay vì chỉ con số. Trạng thái khác
 * (xong, đã đóng) không đưa vào danh sách chờ nên trả chuỗi rỗng.
 */
export function peopleAheadText(entry: VisitQueuePosition): string {
  switch (entry.status) {
    case "BEING_SERVED":
      return "Đang gọi bạn";
    case "WAITING":
      return entry.peopleAhead === 0
        ? "Bạn là người tiếp theo."
        : `Còn ${entry.peopleAhead} người trước bạn.`;
    case "ABSENT":
      return "Bạn đang vắng mặt. Vui lòng đến quầy lễ tân để được gọi lại.";
    default:
      return "";
  }
}
