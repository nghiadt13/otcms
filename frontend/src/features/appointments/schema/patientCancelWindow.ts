// Hạn tự hủy lịch của người bệnh (plan 261111-0208 mục 3, A11 và B1): còn ít nhất 120 phút trước giờ
// hẹn. Chỉ định nghĩa hằng số này ở một chỗ; BE vẫn là nơi quyết (dưới hạn thì BE trả APPOINTMENT_CHANGE_TOO_LATE).

/** Số phút tối thiểu trước giờ hẹn để người bệnh tự hủy lịch. */
export const PATIENT_CANCEL_LEAD_MINUTES = 120;

const LEAD_MS = PATIENT_CANCEL_LEAD_MINUTES * 60_000;

/** Còn đủ hạn tự hủy tại thời điểm `nowMs` (epoch ms). Đúng 120 phút vẫn hủy được. */
export function isPatientCancellable(startAt: string, nowMs: number): boolean {
  return Date.parse(startAt) - nowMs >= LEAD_MS;
}
