import { formatAppMessage } from "@/lib/messages/appMessages";

/** Câu lỗi khoảng ngày cho DateRangeFilter / ô ngày (MSG-54); không lỗi thì undefined. */
export function messageFromRangeError(
  code: "MSG-54" | null,
): string | undefined {
  return code ? formatAppMessage(code) : undefined;
}

/** Toast thành công sau khi chuyển lịch (spec MSG-27, có tên bác sĩ). */
export const reassignSuccessText = (doctorName: string) =>
  `Đã chuyển lịch hẹn sang bác sĩ ${doctorName}. Bệnh nhân sẽ nhận được thông báo.`;

/** Toast sau "Báo cho bệnh nhân" (copy đề xuất, chưa có MSG riêng). */
export const NOTIFY_SUCCESS_TEXT = "Đã gửi thông báo cho bệnh nhân.";

export const AF1_LINES = [
  "Không có bác sĩ nào có ca vào giờ này.",
  "Admin không thể hủy lịch hẹn.",
] as const;
