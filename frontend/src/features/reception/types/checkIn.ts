import type {
  AppointmentStatus,
  ExamFeeStatus,
  PaymentMethod,
  PaymentStatus,
  QueueEntryStatus,
  QueueType,
  VisitType,
} from "@/lib/domain/clinicVisit";

// Hợp đồng dữ liệu của màn Tiếp nhận (UC29, phần phí khám của UC54). Tên trường theo JSON của BE
// (camelCase), enum giữ nguyên UPPER_SNAKE. Chi tiết endpoint: api-notes/S04b.md.

/** Một dòng trong "Lịch hẹn hôm nay". */
export interface TodayAppointmentRow {
  appointmentId: string;
  startAt: string;
  patientName: string;
  patientCode: string;
  phone: string;
  serviceName: string;
  doctorName: string;
  roomName: string;
  isFollowUp: boolean;
  examFeeStatus: ExamFeeStatus;
  status: AppointmentStatus;
}

export interface TodayAppointmentsResponse {
  items: TodayAppointmentRow[];
  counts: { waiting: number; checkedIn: number };
}

/** Tham số `status`: BOOKED (mặc định), CHECKED_IN, "" = tất cả. */
export type TodayAppointmentStatusFilter = "BOOKED" | "CHECKED_IN" | "";

/** Hai tab của danh sách lịch hẹn hôm nay. */
export type TodayTab = Exclude<TodayAppointmentStatusFilter, "">;

export interface CheckInPatient {
  id: string;
  patientCode: string;
  fullName: string;
  dateOfBirth: string;
  sex: "MALE" | "FEMALE";
  phone: string;
}

export interface ExamFee {
  amount: number;
  status: ExamFeeStatus;
  paidOnline: boolean;
}

export interface CheckInServiceOption {
  id: string;
  name: string;
  price: number;
}

export type CheckInOption =
  | {
      kind: "APPOINTMENT";
      appointmentId: string;
      startAt: string;
      serviceName: string;
      visitType: VisitType;
      doctorName: string;
      roomName: string;
      examFee: ExamFee;
    }
  | {
      kind: "RETURN_VISIT";
      visitId: string;
      firstCheckInDate: string;
      dayOfPeriod: number;
      nextQueue: {
        type: Exclude<QueueType, "EXAMINATION">;
        roomName: string;
        doctorName: string;
      };
      /** Luôn NOT_REQUIRED: quay lại trong 7 ngày không thu phí (BR-66). */
      examFee: ExamFee;
    }
  | { kind: "WALK_IN"; services: CheckInServiceOption[] };

export type IneligibleReason =
  | "ALREADY_CHECKED_IN"
  | "CANCELLED"
  | "NO_SHOW"
  | "NOT_TODAY";

export interface IneligibleAppointment {
  appointmentId: string;
  startAt: string;
  reason: IneligibleReason;
  checkedIn?: { at: string; queueNumber: number; roomName: string };
}

export interface CheckInContext {
  patient: CheckInPatient;
  options: CheckInOption[];
  ineligible: IneligibleAppointment[];
  notice: "PREVIOUS_VISIT_INCOMPLETE" | null;
}

/** Query của GET check-in-context: đúng một trong hai trường. */
export type CheckInContextQuery = { appointmentId: string } | { patientId: string };

/** Đích đang chọn trên màn: lịch hẹn (từ danh sách) hoặc bệnh nhân (từ ô tìm). */
export type CheckInTarget =
  | { kind: "appointment"; id: string }
  | { kind: "patient"; id: string };

export interface PriorityGroup {
  code: string;
  label: string;
}

export interface ExamFeePaymentRequest {
  patientId: string;
  appointmentId: string | null;
  serviceId: string | null;
  method: PaymentMethod;
}

export interface VnpayPaymentInfo {
  qrImageUrl: string;
  transactionRef: string;
  expiresAt: string;
}

export interface ExamFeePayment {
  id: string;
  method: PaymentMethod;
  amount: number;
  status: PaymentStatus;
  receiptNo: string | null;
  vnpay: VnpayPaymentInfo | null;
}

export interface CheckInRequest {
  patientId: string;
  /** Có khi tiếp nhận theo lịch hẹn. */
  appointmentId: string | null;
  /** Có khi quay lại trong 7 ngày. */
  visitId: string | null;
  /** Có khi vãng lai. */
  serviceId: string | null;
  priorityGroupCode: string | null;
  /** Khi vừa thu phí tại quầy/VNPay trong màn này. */
  examFeePaymentId: string | null;
}

export interface CheckInResult {
  visitId: string;
  queueEntryId: string;
  queueNumber: number;
  queueType: QueueType;
  roomName: string;
  doctorName: string;
  priorityLabel: string | null;
  peopleAhead: number;
}

/**
 * Bản ghi hàng đợi do tiếp nhận tạo. Kho chung `receptionMockStore` giữ danh sách này; phase
 * hàng đợi tiếp nhận (S05) đọc qua `listCheckInQueueEntries()`.
 */
export interface CheckInQueueEntry {
  id: string;
  visitId: string;
  patientId: string;
  patientName: string;
  patientCode: string;
  appointmentId: string | null;
  queueType: QueueType;
  queueNumber: number;
  roomName: string;
  doctorName: string;
  priorityLabel: string | null;
  checkedInAt: string;
  status: QueueEntryStatus;
}
