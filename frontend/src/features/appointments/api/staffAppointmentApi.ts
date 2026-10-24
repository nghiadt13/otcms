import { mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import type {
  ItemsResponse,
  PageQuery,
  PageResponse,
} from "@/lib/api/pagination";
import { todayIsoDate } from "@/lib/format/formatDate";
import { getLinkedProfileRecords } from "@/lib/patient-scope/linkedProfilesMockStore";
import type { DoctorLookupItem, EligibleDoctor } from "../types/appointment";
import type { BookingPatient, StoredAppointment } from "../types/booking";
import type {
  StaffAppointment,
  StaffAppointmentQuery,
} from "../types/staffAppointment";
import { acceptsBookingsOn } from "./bookingAvailability";
import { BOOKING_DOCTORS, resolveBookingPatient } from "./bookingMockCatalog";
import {
  bookingMockNow,
  changeStoredAppointmentDoctor,
  readStoredAppointments,
} from "./bookingMockStore";
import {
  allowedActionsFor,
  eligibleDoctorsFor,
  matchesStaffFilters,
  toStaffAppointment,
  validateStaffListQuery,
} from "./staffAppointmentMockRules";
import { isNotified, markNotified } from "./staffAppointmentMockStore";

// LỚP API GIẢ của khu Lễ tân: danh sách lịch theo ngày, tìm người đặt, chuyển bác sĩ và báo bệnh nhân.
// Chữ ký khớp REST thật (xem api-notes/S02.md). Khi nối BE, thay thân từng hàm bằng apiRequest;
// màn hình không đổi. Không có hàm hủy lịch hay đổi giờ: Lễ tân không có các thao tác đó (BR-22).
// Chuyển bác sĩ ghi vào kho lịch chung, nên khu bệnh nhân thấy bác sĩ mới ngay.

const latency = (signal?: AbortSignal) =>
  mockDelay(
    process.env.NODE_ENV === "test" ? 0 : 300 + Math.random() * 200,
    signal,
  );

/** Ngày hôm nay theo đồng hồ máy chủ của kho giả (giờ VN). */
const serverToday = () => todayIsoDate(bookingMockNow());

/** Lịch theo mã, hoặc 404 MSG-29 (không có hoặc ngoài quyền của người gọi). */
function recordOrFail(
  records: readonly StoredAppointment[],
  appointmentId: string,
): StoredAppointment {
  return (
    records.find((record) => record.id === appointmentId) ??
    mockFail(404, "MSG-29")
  );
}

/** Dòng hiển thị của một lịch; thao tác tính theo ngày của máy chủ. */
function viewOf(
  records: readonly StoredAppointment[],
  record: StoredAppointment,
): StaffAppointment {
  const today = serverToday();
  const hasReplacement = eligibleDoctorsFor(record, records, today).length > 0;
  return toStaffAppointment(
    record,
    allowedActionsFor(record, hasReplacement, isNotified(record.id), today),
  );
}

/** Dòng hiển thị đọc lại từ kho, sau khi một thao tác đã ghi. */
function viewById(appointmentId: string): StaffAppointment {
  const records = readStoredAppointments();
  return viewOf(records, recordOrFail(records, appointmentId));
}

/**
 * GET /api/v1/appointments?date=&doctorId=&status=&q=&page=&size=&sort=startAt,asc.
 * `date` mặc định hôm nay. Sắp theo giờ tăng dần.
 */
export async function listStaffAppointments(
  query: StaffAppointmentQuery & PageQuery,
  signal?: AbortSignal,
): Promise<PageResponse<StaffAppointment>> {
  await latency(signal);
  validateStaffListQuery(query);
  const date = query.date ?? serverToday();
  const records = readStoredAppointments();
  const items = records
    .filter((record) => matchesStaffFilters(record, query, date))
    .map((record) => viewOf(records, record));
  return paginate(items, query);
}

/**
 * GET /api/v1/appointments/booking-patients/{patientId}. `patientId` là mã từ tìm bệnh nhân
 * (ví dụ `pat-an`); trả đúng mã người đặt lịch được (hồ sơ gắn tài khoản nếu có).
 * Không có → 404 MSG-29; bị khóa đặt lịch → 403 MSG-29.
 */
export async function getBookingPatient(
  patientId: string,
  signal?: AbortSignal,
): Promise<BookingPatient> {
  await latency(signal);
  const patient = resolveBookingPatient(patientId, getLinkedProfileRecords());
  if (!patient) mockFail(404, "MSG-29");
  if (patient.locked) mockFail(403, "MSG-29");
  return {
    id: patient.id,
    fullName: patient.fullName,
    dateOfBirth: patient.dateOfBirth,
    relationship: null,
  };
}

/**
 * GET /api/v1/appointments/doctors: bác sĩ cho bộ lọc của Lễ tân. `bookable` = đang nhận đặt mới
 * hôm nay (có ca, không vắng).
 */
export async function listStaffDoctors(
  signal?: AbortSignal,
): Promise<ItemsResponse<DoctorLookupItem>> {
  await latency(signal);
  const today = serverToday();
  return {
    items: BOOKING_DOCTORS.map((doctor) => ({
      id: doctor.id,
      fullName: doctor.fullName,
      bookable: acceptsBookingsOn(doctor, today, today),
    })),
  };
}

/** GET /api/v1/appointments/{id}/eligible-doctors: bác sĩ có ca cùng giờ. Lịch không Đã đặt → 409 MSG-32. */
export async function listStaffEligibleDoctors(
  appointmentId: string,
  signal?: AbortSignal,
): Promise<ItemsResponse<EligibleDoctor>> {
  await latency(signal);
  const records = readStoredAppointments();
  const record = recordOrFail(records, appointmentId);
  if (record.status !== "BOOKED") mockFail(409, "MSG-32");
  return { items: eligibleDoctorsFor(record, records, serverToday()) };
}

/**
 * POST /api/v1/appointments/{id}/reassign, body `{ doctorId }`. Giờ không đổi. Bác sĩ không còn
 * trong danh sách chuyển được → 409 MSG-24; lịch không Đã đặt → 409 MSG-32.
 */
export async function reassignStaffAppointmentDoctor(
  appointmentId: string,
  doctorId: string,
): Promise<StaffAppointment> {
  await latency();
  const records = readStoredAppointments();
  const record = recordOrFail(records, appointmentId);
  if (!doctorId)
    mockFail(400, "MSG-12", undefined, [{ field: "doctorId", code: "MSG-12" }]);
  if (record.status !== "BOOKED") mockFail(409, "MSG-32");
  const target = eligibleDoctorsFor(record, records, serverToday()).find(
    (doctor) => doctor.id === doctorId,
  );
  if (!target) mockFail(409, "MSG-24");
  const room =
    BOOKING_DOCTORS.find((doctor) => doctor.id === target.id)?.room ??
    record.roomName;
  changeStoredAppointmentDoctor(appointmentId, { doctor: target, roomName: room });
  return viewById(appointmentId);
}

/**
 * POST /api/v1/appointments/{id}/notify-no-replacement. Chỉ khi lịch Đã đặt, không có bác sĩ thay
 * và chưa báo; lịch vẫn Đã đặt. Không đủ điều kiện → 409 MSG-32.
 */
export async function notifyStaffNoReplacement(
  appointmentId: string,
): Promise<StaffAppointment> {
  await latency();
  const records = readStoredAppointments();
  const record = recordOrFail(records, appointmentId);
  const hasReplacement =
    eligibleDoctorsFor(record, records, serverToday()).length > 0;
  if (
    record.status !== "BOOKED" ||
    hasReplacement ||
    isNotified(appointmentId)
  )
    mockFail(409, "MSG-32");
  markNotified(appointmentId);
  return viewById(appointmentId);
}
