import type {
  AppointmentStatus,
  ExamFeeStatus,
} from "@/lib/domain/clinicVisit";
import type { DoctorRef } from "./appointment";
import type { BookedFor } from "./booking";

// Hình khớp JSON hợp đồng `GET /api/v1/appointments` (api-notes/S02.md). Lễ tân không thấy tiền sử,
// dị ứng (BR-12): không có trường nào ngoài hợp đồng.

/** Thao tác BE cho phép trên một lịch. Lễ tân không có hủy hay đổi giờ (BR-22). */
export type StaffAppointmentAction =
  | "checkIn"
  | "reassign"
  | "notifyNoReplacement";

export interface StaffAppointment {
  id: string;
  /** ISO +07:00 */
  startAt: string;
  patient: {
    id: string;
    patientCode: string;
    fullName: string;
    /** YYYY-MM-DD */
    dateOfBirth: string;
    /** null khi hồ sơ chưa có số điện thoại. */
    phone: string | null;
  };
  bookedFor: BookedFor;
  service: { id: string; name: string };
  isFollowUp: boolean;
  doctor: DoctorRef;
  roomName: string;
  examFee: { amount: number; status: ExamFeeStatus };
  status: AppointmentStatus;
  allowedActions: StaffAppointmentAction[];
}

/** Bộ lọc danh sách. `date` mặc định hôm nay (giờ VN). `q` là dữ liệu cá nhân nên không lên URL. */
export interface StaffAppointmentQuery {
  /** YYYY-MM-DD */
  date?: string;
  doctorId?: string;
  status?: AppointmentStatus;
  /** Tên hoặc số điện thoại. */
  q?: string;
}
