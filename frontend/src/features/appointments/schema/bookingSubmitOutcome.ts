import { isApiError } from "@/lib/api/apiError";

// Cách phục hồi sau khi POST lịch lỗi. Toast chung đã do notifyError hiển thị; file này chỉ quyết
// định việc cần làm với form.

export type BookingSubmitOutcome =
  /** MSG-30: giờ đã bị người khác đặt. Bỏ giờ, tải lại giờ trống, đưa focus về lưới giờ. */
  | "slotTaken"
  /** MSG-24: bác sĩ đã chọn không còn nhận. Về "Phòng khám sắp xếp", tải lại giờ trống. */
  | "doctorUnavailable"
  /** Mọi lỗi khác (MSG-29, MSG-31, MSG-04, lỗi mạng): giữ nguyên lựa chọn để người dùng sửa. */
  | "keepForm";

export function bookingSubmitOutcome(error: unknown): BookingSubmitOutcome {
  if (!isApiError(error)) return "keepForm";
  if (error.code === "MSG-30") return "slotTaken";
  if (error.code === "MSG-24") return "doctorUnavailable";
  return "keepForm";
}
