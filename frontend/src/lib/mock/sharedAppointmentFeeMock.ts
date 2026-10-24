import type { CareRelationship } from "@/lib/domain/careRelationship";
import type { AppointmentStatus, ExamFeeStatus } from "@/lib/domain/clinicVisit";

// Kho giả dùng chung: phí khám của từng lịch hẹn. Cho khu thanh toán thấy lịch của kho đặt lịch
// (feature `appointments`) mà không import feature đó. Chỉ cho lớp API giả, xóa khi nối BE; không
// chứa luật nghiệp vụ. Ai ghi: kho đặt lịch (nạp dữ liệu, đặt mới, hủy) và thanh toán (khi giao
// dịch thành công). Chỉ lịch của hồ sơ đã gắn được đăng ký; thanh toán lọc tiếp theo hồ sơ gắn.

/** Dữ kiện phí khám của một lịch hẹn, đủ để thanh toán liệt kê và đánh dấu đã trả. */
export interface MockAppointmentFee {
  appointmentId: string;
  /** Mã hồ sơ đã gắn (`LinkedProfile.id`) của người được khám. */
  profileId: string;
  patientName: string;
  relationship: CareRelationship;
  /** ISO +07:00 */
  startAt: string;
  serviceName: string;
  /** VND nguyên, giá chốt lúc đặt (BR-59). */
  examFeeAmount: number;
  examFeeStatus: ExamFeeStatus;
  /** Chỉ lịch Đã đặt còn khoản phải trả. */
  appointmentStatus: AppointmentStatus;
}

const fees = new Map<string, MockAppointmentFee>();

/** Ghi hoặc thay phí theo `appointmentId`. Lưu bản sao để bên gọi sửa đối tượng không đổi kho. */
export function upsertMockAppointmentFee(fee: MockAppointmentFee): void {
  fees.set(fee.appointmentId, { ...fee });
}

/** Tra phí của một lịch: trả bản sao, hoặc `undefined` khi lịch chưa được đăng ký. */
export function findMockAppointmentFee(
  appointmentId: string,
): MockAppointmentFee | undefined {
  const found = fees.get(appointmentId);
  return found ? { ...found } : undefined;
}

/** Mọi phí đã đăng ký, bản sao. */
export function listMockAppointmentFees(): MockAppointmentFee[] {
  return [...fees.values()].map((fee) => ({ ...fee }));
}

/**
 * Giao dịch thành công: phí của lịch thành đã trả. Lịch không có trong kho (ví dụ lịch do kho
 * thanh toán giữ riêng) thì không làm gì.
 */
export function markMockAppointmentFeePaid(appointmentId: string): void {
  const fee = fees.get(appointmentId);
  if (fee) fees.set(appointmentId, { ...fee, examFeeStatus: "PAID" });
}

/**
 * Lịch bị hủy: không còn là khoản phải trả. Chỉ đổi trạng thái lịch; phí đã trả giữ nguyên
 * (BR-53, không hoàn tiền). Lịch không có trong kho thì không làm gì.
 */
export function markMockAppointmentCancelled(appointmentId: string): void {
  const fee = fees.get(appointmentId);
  if (fee) fees.set(appointmentId, { ...fee, appointmentStatus: "CANCELLED" });
}

/** Chỉ dùng trong test và demo: xóa kho. Kho đặt lịch nạp lại dữ liệu mẫu sau khi xóa. */
export function resetMockAppointmentFees(): void {
  fees.clear();
}
