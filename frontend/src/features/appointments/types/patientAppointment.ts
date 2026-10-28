import type { CareRelationship } from "@/lib/domain/careRelationship";
import type {
  AppointmentStatus,
  ExamFeeStatus,
} from "@/lib/domain/clinicVisit";
import type { DoctorRef } from "./appointment";

// Lịch hẹn nhìn từ khu bệnh nhân (UC23, UC24): hình trả của GET /api/v1/appointments và
// GET /api/v1/appointments/{id}. Khác BookedAppointment (kết quả đặt): có người khám kèm quan hệ,
// lượt khám, cờ bác sĩ vắng và `allowedActions` (BE quyết; FE chỉ vẽ nút có trong mảng).
// Ngày theo YYYY-MM-DD, thời điểm ISO có +07:00, tiền số nguyên VND, enum UPPER_SNAKE.

/** Việc người bệnh làm được với một lịch. */
export type PatientAppointmentAction = "cancel" | "payExamFee";

/** Tab: sắp tới (Đã đặt, sớm trước) hoặc đã qua (các trạng thái khác, mới trước). */
export type AppointmentWhen = "upcoming" | "past";

export interface PatientAppointment {
  id: string;
  /** ISO +07:00 */
  startAt: string;
  /** Luôn ghi "của ai": tên và quan hệ với tài khoản. */
  patient: { id: string; fullName: string; relationship: CareRelationship };
  /** Có khi đã check-in, để mở tiến độ lượt khám. */
  visitId: string | null;
  service: { id: string; name: string };
  doctor: DoctorRef;
  roomName: string;
  isFollowUp: boolean;
  status: AppointmentStatus;
  examFee: { amount: number; status: ExamFeeStatus };
  /** Bác sĩ vắng và chưa có người thay (UC22 AF1): lịch vẫn Đã đặt. */
  doctorUnavailable: boolean;
  allowedActions: PatientAppointmentAction[];
}

export interface PatientAppointmentQuery {
  when: AppointmentWhen;
  /** Mã hồ sơ đã gắn (`LinkedProfile.id`); bỏ trống = mọi hồ sơ của tài khoản. */
  patientId?: string;
}
