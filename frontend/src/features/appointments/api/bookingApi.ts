import { clone, mockDelay, mockFail } from "@/lib/api/mockServer";
import type { ItemsResponse } from "@/lib/api/pagination";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import { vnIsoAt, vnIsoOf } from "../schema/bookingTime";
import type {
  BookableService,
  BookedAppointment,
  CreateAppointmentRequest,
  OpenTimesQuery,
  OpenTimesResponse,
  StoredAppointment,
} from "../types/booking";
import {
  BOOKABLE_SERVICES,
  HORIZON_DAYS,
  SLOT_MINUTES,
  TAKEN_TODAY_TIME,
} from "./bookingMockCatalog";
import { horizonEnd, openDaysFrom } from "./bookingAvailability";
import {
  chooseDoctor,
  invalidBooking,
  readCreateRequest,
  requireBookablePatient,
  requireBookableService,
  validateOpenTimesRange,
} from "./bookingRequestRules";
import {
  appendStoredAppointment,
  bookingMockNow,
  hasBookedPatientAt,
  isStartTaken,
  markStartTaken,
  nextAppointmentId,
} from "./bookingMockStore";

// LỚP API GIẢ của luồng đặt lịch. Chữ ký khớp REST thật (xem api-notes/P03.md). Khi nối BE, thay
// thân từng hàm bằng apiRequest; màn hình không đổi. Lỗi giả đúng hình ProblemDetail + mã MSG.

const latency = (signal?: AbortSignal) =>
  mockDelay(
    process.env.NODE_ENV === "test" ? 0 : 300 + Math.random() * 200,
    signal,
  );

/** Chiếu bản ghi kho về đúng hình BookedAppointment (bỏ SĐT, mã BN, người đặt). */
function toBookedAppointment(record: StoredAppointment): BookedAppointment {
  return clone({
    id: record.id,
    startAt: record.startAt,
    patient: record.patient,
    service: record.service,
    doctor: record.doctor,
    roomName: record.roomName,
    status: record.status,
    examFee: record.examFee,
    isFollowUp: record.isFollowUp,
  });
}

/** GET /api/v1/appointments/bookable-services */
export async function listBookableServices(
  signal?: AbortSignal,
): Promise<ItemsResponse<BookableService>> {
  await latency(signal);
  return { items: clone([...BOOKABLE_SERVICES]) };
}

/**
 * GET /api/v1/appointments/open-times. Mặc định từ hôm nay, tối đa 14 ngày. Giờ đã qua so với
 * `serverNow` không được trả về; FE vẫn lọc lại theo `serverNow` phòng lệch đồng hồ.
 */
export async function getOpenTimes(
  query: OpenTimesQuery,
  signal?: AbortSignal,
): Promise<OpenTimesResponse> {
  await latency(signal);
  if (!query.serviceId) invalidBooking(["serviceId"]);
  if (!query.patientId) invalidBooking(["patientId"]);
  const now = bookingMockNow();
  const today = todayIsoDate(now);
  const from = query.from ?? today;
  const to = query.to ?? addDays(from, HORIZON_DAYS - 1);
  validateOpenTimesRange(from, to, today);
  requireBookableService(query.serviceId);
  requireBookablePatient(query.patientId);
  return {
    serviceId: query.serviceId,
    from,
    to,
    slotMinutes: SLOT_MINUTES,
    serverNow: vnIsoOf(now),
    days: openDaysFrom(from, to, today, now),
  };
}

/**
 * POST /api/v1/appointments. Giờ không còn trống (đã có người đặt, đã qua, hoặc đã bị giữ) → 409
 * MSG-30. Kịch bản `TAKEN_TODAY_TIME`: giờ đó của hôm nay luôn bị người khác giữ trước.
 */
export async function createAppointment(
  body: CreateAppointmentRequest,
  signal?: AbortSignal,
): Promise<BookedAppointment> {
  await latency(signal);
  const request = readCreateRequest(body);
  const service = requireBookableService(request.serviceId);
  const patient = requireBookablePatient(request.patientId);

  const now = bookingMockNow();
  const today = todayIsoDate(now);
  const { startAt, date } = request;
  if (Date.parse(startAt) < now.getTime()) mockFail(409, "MSG-30");
  if (isStartTaken(startAt)) mockFail(409, "MSG-30");
  if (startAt === vnIsoAt(today, TAKEN_TODAY_TIME)) {
    markStartTaken(startAt);
    mockFail(409, "MSG-30");
  }
  if (date > horizonEnd(today)) invalidBooking(["startAt"]);
  if (hasBookedPatientAt(patient.id, startAt)) invalidBooking(["startAt"]);

  const doctor = chooseDoctor(request.doctorId, startAt, date, today);
  const record: StoredAppointment = {
    id: nextAppointmentId(),
    startAt,
    patient: { id: patient.id, fullName: patient.fullName },
    service: { id: service.id, name: service.name },
    doctor: { id: doctor.id, fullName: doctor.fullName },
    roomName: doctor.room,
    status: "BOOKED",
    examFee: { amount: service.price, status: "PENDING_PAYMENT" },
    isFollowUp: false,
    patientCode: patient.patientCode,
    patientDateOfBirth: patient.dateOfBirth,
    patientPhone: patient.phone,
    bookedFor: patient.bookedFor,
  };
  appendStoredAppointment(record);
  return toBookedAppointment(record);
}
