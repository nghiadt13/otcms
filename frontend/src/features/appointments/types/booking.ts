import type { CareRelationship } from "@/lib/domain/careRelationship";
import type {
  AppointmentStatus,
  ExamFeeStatus,
} from "@/lib/domain/clinicVisit";
import type { DoctorRef } from "./appointment";

// Hình khớp JSON hợp đồng API đặt lịch (plan 261024-0512 phase-03 mục 6–7). Ngày YYYY-MM-DD,
// thời điểm ISO có offset +07:00, tiền số nguyên VND, enum UPPER_SNAKE.

/** Cỡ chữ và chạm: `portal` (khu bệnh nhân, chữ lớn) hoặc `staff` (khu Lễ tân, công cụ). */
export type BookingVariant = "patient" | "staff";

/** Người khám đã chọn. Khu bệnh nhân: hồ sơ đã gắn; khu Lễ tân: bệnh nhân đã tìm được. */
export interface BookingPatient {
  id: string;
  fullName: string;
  /** YYYY-MM-DD */
  dateOfBirth: string;
  /** Quan hệ với tài khoản; khu Lễ tân luôn `null`. */
  relationship: CareRelationship | null;
}

/** Dịch vụ Khám đặt trực tuyến được (BR-60). Giá là giá hiện hành của BE. */
export interface BookableService {
  id: string;
  name: string;
  /** VND */
  price: number;
  bookingDetails: string | null;
}

/** Một giờ trống và các bác sĩ còn trống ở giờ đó (BR-20). */
export interface OpenTime {
  /** ISO +07:00 */
  startAt: string;
  doctors: DoctorRef[];
}

export interface OpenDay {
  /** YYYY-MM-DD */
  date: string;
  times: OpenTime[];
}

export interface OpenTimesResponse {
  serviceId: string;
  /** YYYY-MM-DD */
  from: string;
  /** YYYY-MM-DD */
  to: string;
  slotMinutes: number;
  /** ISO +07:00; FE ẩn giờ trước thời điểm này để không lệch đồng hồ máy. */
  serverNow: string;
  days: OpenDay[];
}

export interface OpenTimesQuery {
  serviceId: string;
  /** Để BE kiểm quyền đặt cho người này (BR-21). */
  patientId: string;
  /** YYYY-MM-DD; mặc định hôm nay. */
  from?: string;
  /** YYYY-MM-DD; mặc định `from` + 13 ngày, tối đa 14 ngày. */
  to?: string;
}

export interface CreateAppointmentRequest {
  patientId: string;
  serviceId: string;
  /** ISO +07:00 */
  startAt: string;
  /** null = phòng khám sắp xếp bác sĩ (BE gán một bác sĩ có ca giờ đó, UC21 N1.3). */
  doctorId: string | null;
}

/** Lịch vừa đặt, hình trả về của POST. Không có SĐT, mã bệnh nhân hay thông tin nội bộ. */
export interface BookedAppointment {
  id: string;
  /** ISO +07:00 */
  startAt: string;
  patient: { id: string; fullName: string };
  service: { id: string; name: string };
  doctor: DoctorRef;
  roomName: string;
  status: AppointmentStatus;
  /** Giá chốt lúc đặt (BR-59). */
  examFee: { amount: number; status: ExamFeeStatus };
  isFollowUp: boolean;
}

/** Đặt cho chính chủ hay người phụ thuộc đã gắn (BR-21). */
export type BookedFor =
  | { kind: "SELF" }
  | { kind: "DEPENDENT"; accountHolderName: string };

/**
 * Bản ghi đầy đủ trong kho giả dùng chung (xem api/bookingMockStore.ts). Các trường thêm so
 * với BookedAppointment chỉ để màn Lễ tân và danh sách lịch hẹn của bệnh nhân đọc; lớp API
 * trả ra ngoài phải chiếu về BookedAppointment.
 */
export interface StoredAppointment extends BookedAppointment {
  patientCode: string;
  patientDateOfBirth: string;
  /** null khi kho giả không có SĐT của người này. */
  patientPhone: string | null;
  bookedFor: BookedFor;
}
