import { isApiError } from "@/lib/api/apiError";

// Cách phục hồi sau khi POST lịch lỗi. Toast chung đã do notifyError hiển thị; file này chỉ quyết
// định việc cần làm với form.

export type BookingSubmitOutcome =
  /** MSG-30: giờ đã bị người khác đặt. Bỏ giờ, tải lại giờ trống, đưa focus về lưới giờ. */
  | "slotTaken"
  /** Đặt online có phí quá sát giờ khám (BOOKING_TOO_SOON): bỏ giờ, tải lại giờ trống để chọn giờ khác. */
  | "tooSoon"
  /** MSG-24: bác sĩ đã chọn không còn nhận. Về "Phòng khám sắp xếp", tải lại giờ trống. */
  | "doctorUnavailable"
  /** Mọi lỗi khác (MSG-29, MSG-31, MSG-04, lỗi mạng): giữ nguyên lựa chọn để người dùng sửa. */
  | "keepForm";

/** Mã lỗi thô của BE khi đặt online dịch vụ có phí cách giờ khám dưới 15 phút. */
const BOOKING_TOO_SOON = "BOOKING_TOO_SOON";

/**
 * Chưa có mã MSG trong R3 cho tình huống này: dùng chữ không mã tới khi nhóm gán. "15 phút" cứng ở
 * đây vì lỗi BOOKING_TOO_SOON của BE không kèm số phút; đổi `appointment.booking.payment-hold-minutes`
 * thì sửa chữ này (hoặc cho BE trả số phút trong `params`).
 */
export const BOOKING_TOO_SOON_TEXT =
  "Cần đặt trước giờ khám ít nhất 15 phút";

export function bookingSubmitOutcome(error: unknown): BookingSubmitOutcome {
  if (!isApiError(error)) return "keepForm";
  if (error.errorCode === BOOKING_TOO_SOON) return "tooSoon";
  if (error.code === "MSG-30") return "slotTaken";
  if (error.code === "MSG-24") return "doctorUnavailable";
  return "keepForm";
}

/**
 * Câu hiển thị riêng cho lỗi chưa có mã MSG (`messageFromError` sẽ rơi về MSG-04 cho các lỗi này).
 * `null` = dùng toast chung theo mã MSG.
 */
export function bookingSubmitErrorText(error: unknown): string | null {
  return isApiError(error) && error.errorCode === BOOKING_TOO_SOON
    ? BOOKING_TOO_SOON_TEXT
    : null;
}
