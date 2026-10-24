import { clone, mockFail } from "@/lib/api/mockServer";
import type { AppointmentStatus } from "@/lib/domain/clinicVisit";
import { dateOfStart, hhmmOfStart } from "../schema/bookingTime";
import { STATUS_VALUES } from "../schema/appointmentUrlRules";
import type { DoctorRef } from "../types/appointment";
import type { StoredAppointment } from "../types/booking";
import type {
  StaffAppointment,
  StaffAppointmentAction,
  StaffAppointmentQuery,
} from "../types/staffAppointment";
import { acceptsBookingsOn, SLOT_START_TIMES } from "./bookingAvailability";
import { BOOKING_DOCTORS } from "./bookingMockCatalog";

// Luật và cách chiếu dữ liệu của lớp API giả khu Lễ tân. Hàm thuần: nhận danh sách lịch trong kho
// và trả kết quả, không đọc hay ghi kho. Lỗi ném đúng hình ProblemDetail + mã MSG.

/** Trạng thái còn giữ giờ: người khác không đặt được cùng bác sĩ và giờ này. */
const HOLDS_SLOT: readonly AppointmentStatus[] = ["BOOKED", "CHECKED_IN"];
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

/** `YYYY-MM-DD` có thật trên lịch (loại 2026-02-30). */
export function isValidIsoDate(value: string): boolean {
  if (!DATE_PATTERN.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return (
    !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value
  );
}

/** Bộ lọc sai dạng → 400 MSG-54 đúng ô. */
export function validateStaffListQuery(query: StaffAppointmentQuery): void {
  const fields: string[] = [];
  if (query.date !== undefined && !isValidIsoDate(query.date)) fields.push("date");
  if (query.status !== undefined && !STATUS_VALUES.includes(query.status))
    fields.push("status");
  if (fields.length > 0)
    mockFail(
      400,
      "MSG-54",
      undefined,
      fields.map((field) => ({ field, code: "MSG-54" as const })),
    );
}

/**
 * Bác sĩ chuyển được tới cùng giờ: có ca và đang nhận đặt vào ngày đó, và không có lịch còn giữ
 * giờ đó (trừ chính lịch đang xét). Danh sách rỗng = luồng AF1.
 */
export function eligibleDoctorsFor(
  record: StoredAppointment,
  records: readonly StoredAppointment[],
  today: string,
): DoctorRef[] {
  const { id, startAt } = record;
  if (!SLOT_START_TIMES.includes(hhmmOfStart(startAt))) return [];
  const date = dateOfStart(startAt);
  return BOOKING_DOCTORS.filter(
    (doctor) =>
      doctor.id !== record.doctor.id &&
      acceptsBookingsOn(doctor, date, today) &&
      !records.some(
        (other) =>
          other.id !== id &&
          other.doctor.id === doctor.id &&
          other.startAt === startAt &&
          HOLDS_SLOT.includes(other.status),
      ),
  ).map(({ id: doctorId, fullName }) => ({ id: doctorId, fullName }));
}

/**
 * Thao tác FE được vẽ (BR-22: Lễ tân không hủy, không đổi giờ). `checkIn` khi lịch Đã đặt của
 * hôm nay; `reassign` khi Đã đặt; `notifyNoReplacement` khi Đã đặt, không có bác sĩ thay và chưa báo.
 */
export function allowedActionsFor(
  record: StoredAppointment,
  hasReplacement: boolean,
  notified: boolean,
  today: string,
): StaffAppointmentAction[] {
  if (record.status !== "BOOKED") return [];
  const actions: StaffAppointmentAction[] = [];
  if (dateOfStart(record.startAt) === today) actions.push("checkIn");
  actions.push("reassign");
  if (!hasReplacement && !notified) actions.push("notifyNoReplacement");
  return actions;
}

/** Chiếu về hợp đồng khu Lễ tân: bỏ người đặt và các trường nội bộ của kho. */
export function toStaffAppointment(
  record: StoredAppointment,
  allowedActions: StaffAppointmentAction[],
): StaffAppointment {
  return clone({
    id: record.id,
    startAt: record.startAt,
    patient: {
      id: record.patient.id,
      patientCode: record.patientCode,
      fullName: record.patient.fullName,
      dateOfBirth: record.patientDateOfBirth,
      phone: record.patientPhone,
    },
    bookedFor: record.bookedFor,
    service: record.service,
    isFollowUp: record.isFollowUp,
    doctor: record.doctor,
    roomName: record.roomName,
    examFee: record.examFee,
    status: record.status,
    allowedActions,
  });
}

/** Bỏ dấu và thường hóa để tìm: "hoa" khớp "Hoa", "đức" khớp "Duc". */
function fold(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/đ/g, "d")
    .toLowerCase();
}

/**
 * Lịch thuộc bộ lọc: đúng ngày, đúng bác sĩ và trạng thái nếu có; `q` khớp tên (không phân biệt
 * dấu) hoặc khớp SĐT khi `q` có chữ số.
 */
export function matchesStaffFilters(
  record: StoredAppointment,
  query: StaffAppointmentQuery,
  date: string,
): boolean {
  if (dateOfStart(record.startAt) !== date) return false;
  if (query.doctorId && record.doctor.id !== query.doctorId) return false;
  if (query.status && record.status !== query.status) return false;
  const term = query.q?.trim() ?? "";
  if (!term) return true;
  const digits = term.replace(/\D/g, "");
  return (
    fold(record.patient.fullName).includes(fold(term)) ||
    (digits.length > 0 && (record.patientPhone ?? "").includes(digits))
  );
}
