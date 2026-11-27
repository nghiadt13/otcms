// Trạng thái lịch hẹn trên màn tiếp nhận. Chỉ các trạng thái này được liệt kê trong ngày (BE đã lọc
// giữ giờ thanh toán ra khỏi danh sách lễ tân).
export const DAILY_RECEPTION_STATUSES = new Set([
  "CONFIRMED",
  "NO_SHOW",
  "CHECKED_IN",
  "IN_PROGRESS",
  "COMPLETED",
]);

/** Màn bệnh nhân coi lịch chưa thanh toán là đã hủy khi giờ hẹn đã tới. */
export function effectiveReceptionStatus(
  status: string,
  startTime: string,
  examFeePaid: boolean | null,
  now = Date.now(),
) {
  if (
    status === "CONFIRMED" &&
    examFeePaid !== true &&
    Date.parse(startTime) <= now
  ) {
    return "CANCELLED";
  }
  return status;
}

/** Khoá nhãn trạng thái của `Status` (ReceptionUi) ứng với trạng thái lịch hẹn của BE. */
export function receptionStatusValue(status: string) {
  switch (status) {
    case "NO_SHOW":
      return "no-show";
    case "CHECKED_IN":
      return "checked-in";
    case "IN_PROGRESS":
      return "examining";
    case "COMPLETED":
      return "completed";
    case "CANCELLED":
      return "cancelled";
    // Giữ giờ chờ thanh toán: BE đã ẩn khỏi danh sách lễ tân (DAILY_RECEPTION_STATUSES cũng loại).
    // Có ở đây chỉ để lỡ BE trả ra thì không bị hiện nhầm là "Đã đặt lịch".
    case "AWAITING_PAYMENT":
      return "pending";
    case "PAYMENT_EXPIRED":
      return "cancelled";
    default:
      return "booked";
  }
}
