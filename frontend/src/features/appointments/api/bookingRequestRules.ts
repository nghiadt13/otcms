import type { ApiFieldError } from "@/lib/api/apiError";
import { mockFail } from "@/lib/api/mockServer";
import { getLinkedProfileRecords } from "@/lib/patient-scope/linkedProfilesMockStore";
import type {
  BookableService,
  CreateAppointmentRequest,
} from "../types/booking";
import {
  BOOKABLE_SERVICES,
  FAILING_SERVICE_ID,
  HORIZON_DAYS,
  doctorById,
  resolveBookingPatient,
  type BookingDoctor,
  type ResolvedBookingPatient,
} from "./bookingMockCatalog";
import {
  SLOT_START_TIMES,
  acceptsBookingsOn,
  freeDoctorsAt,
} from "./bookingAvailability";
import { isDoctorBusyAt } from "./bookingMockStore";

// Luật kiểm yêu cầu và chọn bác sĩ của lớp API giả. Mỗi hàm ném đúng hình lỗi BE (ProblemDetail
// + mã MSG). Tách khỏi bookingApi.ts để file endpoint gọn; chỉ bookingApi.ts gọi các hàm này.

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
/** ISO giờ VN đúng bước phút, ví dụ "2026-10-27T10:00:00+07:00". Nhóm 1 = ngày, nhóm 2 = HH:mm. */
export const START_PATTERN = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}):00\+07:00$/;
const DAY_MS = 86_400_000;

/** Lỗi 400 MSG-31 kèm `fieldErrors` các ô sai. */
export function invalidBooking(fields: string[]): never {
  const fieldErrors: ApiFieldError[] = fields.map((field) => ({
    field,
    code: "MSG-31",
  }));
  return mockFail(400, "MSG-31", undefined, fieldErrors);
}

function daysBetween(from: string, to: string): number {
  return (
    (Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / DAY_MS
  );
}

/** Khoảng ngày của open-times: đúng định dạng, không quá khứ, không quá 14 ngày. */
export function validateOpenTimesRange(
  from: string,
  to: string,
  today: string,
): void {
  const fields: string[] = [];
  if (!DATE_PATTERN.test(from)) fields.push("from");
  if (!DATE_PATTERN.test(to)) fields.push("to");
  if (fields.length > 0) invalidBooking(fields);
  if (from < today) invalidBooking(["from"]);
  if (to < from || daysBetween(from, to) > HORIZON_DAYS - 1)
    invalidBooking(["to"]);
}

/** Dịch vụ phải là dịch vụ Khám đang đặt được (BR-60). Không có → 404 MSG-29. */
export function requireBookableService(serviceId: string): BookableService {
  if (serviceId === FAILING_SERVICE_ID) mockFail(500, "MSG-04");
  return (
    BOOKABLE_SERVICES.find((service) => service.id === serviceId) ??
    mockFail(404, "MSG-29")
  );
}

/** Người được đặt: hồ sơ đang gắn hoặc người lễ tân quản lý. Bị khóa → 403 MSG-29. */
export function requireBookablePatient(
  patientId: string,
): ResolvedBookingPatient {
  const patient = resolveBookingPatient(patientId, getLinkedProfileRecords());
  if (!patient) mockFail(404, "MSG-29");
  if (patient.locked) mockFail(403, "MSG-29");
  return patient;
}

export interface ReadCreateRequest {
  patientId: string;
  serviceId: string;
  startAt: string;
  /** YYYY-MM-DD lấy từ startAt. */
  date: string;
  doctorId: string | null;
}

/** Đọc yêu cầu đặt lịch: thiếu hoặc sai dạng → 400 MSG-31 đúng ô. */
export function readCreateRequest(
  body: CreateAppointmentRequest,
): ReadCreateRequest {
  const fields: string[] = [];
  if (!body.patientId) fields.push("patientId");
  if (!body.serviceId) fields.push("serviceId");
  if (body.doctorId !== null && !body.doctorId) fields.push("doctorId");
  const match = START_PATTERN.exec(body.startAt);
  if (!match || !SLOT_START_TIMES.includes(match[2])) fields.push("startAt");
  if (fields.length > 0 || match === null) invalidBooking(fields);
  return {
    patientId: body.patientId,
    serviceId: body.serviceId,
    startAt: body.startAt,
    date: match[1],
    doctorId: body.doctorId,
  };
}

/**
 * Bác sĩ cho lịch. Chọn bác sĩ cụ thể mà họ không còn nhận giờ đó → 409 MSG-24. Chọn "phòng khám
 * sắp xếp" (null) → lấy bác sĩ còn trống đầu tiên (UC21 N1.3); không còn ai → 409 MSG-30.
 */
export function chooseDoctor(
  doctorId: string | null,
  startAt: string,
  date: string,
  today: string,
): BookingDoctor {
  if (doctorId !== null) {
    const doctor = doctorById(doctorId);
    if (
      !doctor ||
      !acceptsBookingsOn(doctor, date, today) ||
      isDoctorBusyAt(doctor.id, startAt)
    )
      mockFail(409, "MSG-24");
    return doctor;
  }
  const [first] = freeDoctorsAt(startAt, today);
  if (!first) mockFail(409, "MSG-30");
  return first;
}
