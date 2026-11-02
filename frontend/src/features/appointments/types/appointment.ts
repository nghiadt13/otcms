import type { PageResponse } from "@/lib/api/pagination";
import type { AppointmentStatus } from "@/lib/domain/clinicVisit";

// Hình khớp 1-1 với JSON hợp đồng API (plan phase-05 mục 7).

// Enum dùng chung ở lib/domain; giữ re-export để các file trong feature không đổi import.
export type { AppointmentStatus };
export type AppointmentAction = "reassign" | "notifyNoReplacement";
export type SlotStatus = "FREE" | "BOOKED";
export type AppointmentTab = "list" | "availability";

export interface DoctorRef {
  id: string;
  fullName: string;
}

export interface Appointment {
  id: string;
  /** ISO có offset +07:00 */
  startAt: string;
  /** Người khám */
  patientName: string;
  /** Số điện thoại chỉ dùng để lọc trong màn quản trị. */
  patientPhone?: string;
  bookedForDependent: boolean;
  serviceName: string;
  /** null = "Chưa chỉ định" */
  doctor: DoctorRef | null;
  status: AppointmentStatus;
  /** Lịch đã qua, đã trả phí và cần lễ tân xử lý. */
  requiresReception?: boolean;
  /** null khi danh sách lịch hẹn chưa nhận được dữ liệu thu phí. */
  examFeePaid: boolean | null;
  /** BE quyết định; FE chỉ vẽ nút có trong mảng. */
  allowedActions: AppointmentAction[];
}

export interface AppointmentListQuery {
  /** YYYY-MM-DD */
  from?: string;
  to?: string;
  doctorId?: string;
  status?: AppointmentStatus;
  /** Tên hoặc SĐT; không đưa lên URL. */
  q?: string;
}

export type EligibleDoctor = DoctorRef;

export interface DoctorLookupItem extends DoctorRef {
  bookable: boolean;
}

export interface AvailabilitySlot {
  startAt: string;
  status: SlotStatus;
}

export interface AvailabilityDay {
  date: string;
  hasShift: boolean;
  slots: AvailabilitySlot[];
}

export interface DoctorAvailability {
  doctorId: string;
  from: string;
  to: string;
  slotMinutes: number;
  days: AvailabilityDay[];
}

export type AppointmentPage = PageResponse<Appointment>;

/** Trạng thái lấy từ URL; trường vắng = dùng mặc định. */
export interface AppointmentUrlState {
  tab: AppointmentTab;
  doctorId?: string;
  from?: string;
  to?: string;
  status?: AppointmentStatus;
}
