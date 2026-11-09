// Chữ cố định của luồng giữ chỗ thanh toán. R3 chưa gán mã MSG cho các tình huống này nên dùng chữ
// không mã tới khi nhóm gán (không tự đặt mã MSG mới).

/** Toast khi giữ chỗ bị hủy vì người dùng rời trang thanh toán (tải lại app, bấm sang trang khác). */
export const HOLD_CANCELLED_ON_LEAVE_TEXT =
  "Giữ chỗ đã hủy vì bạn rời trang thanh toán";

/** Toast khi hủy gặp lúc đã thanh toán: BE xác nhận lịch thay vì hủy. */
export const HOLD_ALREADY_PAID_TEXT =
  "Bạn đã thanh toán, lịch đã được xác nhận";

/** Toast khi tab/thiết bị khác đã hủy giữ chỗ trong lúc đang ở bước thanh toán. */
export const HOLD_CANCELLED_ELSEWHERE_TEXT =
  "Giữ chỗ đã được hủy, hãy đặt lại";

/** Toast sau khi người dùng chủ động hủy ở bước thanh toán. */
export const HOLD_CANCELLED_BY_USER_TEXT =
  "Đã hủy giữ chỗ. Bạn có thể đặt lại lịch khám.";

/** Hộp thoại khi rời bước thanh toán (bấm liên kết hoặc Back). */
export const HOLD_LEAVE_DIALOG = {
  title: "Bạn có chắc muốn hủy thanh toán?",
  description:
    "Nếu rời khỏi trang này, giữ chỗ sẽ bị hủy và giờ khám được mở lại cho người khác.",
  stay: "Tiếp tục thanh toán",
  leave: "Hủy lịch",
  leaving: "Đang hủy...",
} as const;
