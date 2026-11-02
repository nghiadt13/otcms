import { ApiError } from "@/lib/api/apiError";
import type {
  ItemsResponse,
  PageQuery,
  PageResponse,
} from "@/lib/api/pagination";
import { clone, mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import { addDays } from "@/lib/format/formatDate";
import type {
  Appointment,
  AppointmentListQuery,
  AvailabilityDay,
  DoctorAvailability,
  DoctorLookupItem,
  EligibleDoctor,
} from "../types/appointment";
import {
  DOCTORS,
  DOCTOR_IDS,
  SCENARIO_IDS,
  SLOT_MINUTES,
  createInitialAppointments,
  shiftSlotTimes,
  type MockAppointment,
} from "./appointmentMockData";

// Lớp API giả: chữ ký = endpoint thật (/api/v1/admin/...). Nối BE chỉ sửa file này.

let store: MockAppointment[] = createInitialAppointments();

/** Đặt lại kho giả (dùng trong test). */
export function resetAppointmentMock() {
  store = createInitialAppointments();
}

const latency = (signal?: AbortSignal) =>
  mockDelay(
    process.env.NODE_ENV === "test" ? 0 : 300 + Math.random() * 200,
    signal,
  );

function publicView(source: MockAppointment): Appointment {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars -- SĐT chỉ dùng để tìm kiếm, không trả ra ngoài
  const { patientPhone, ...appointment } = source;
  return clone(appointment);
}

/** Bỏ dấu để tìm "hoa" khớp "Hoa", "đức" khớp "duc". */
function fold(text: string) {
  return text
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/đ/g, "d")
    .toLowerCase();
}

function findOrFail(id: string): MockAppointment {
  return store.find((item) => item.id === id) ?? mockFail(404, "MSG-29");
}

/** GET /api/v1/admin/appointments */
export async function listAppointments(
  query: AppointmentListQuery & PageQuery,
  signal?: AbortSignal,
): Promise<PageResponse<Appointment>> {
  await latency(signal);
  if (query.from && query.to && query.from > query.to)
    mockFail(400, "MSG-54", undefined, [{ field: "to", code: "MSG-54" }]);
  const q = query.q ? fold(query.q.trim()) : "";
  const items = store
    .filter((item) => {
      const day = item.startAt.slice(0, 10);
      if (query.from && day < query.from) return false;
      if (query.to && day > query.to) return false;
      if (query.doctorId && item.doctor?.id !== query.doctorId) return false;
      if (query.status && item.status !== query.status) return false;
      return (
        !q ||
        fold(item.patientName).includes(q) ||
        item.patientPhone.includes(q)
      );
    })
    .map(publicView);
  return paginate(items, query);
}

function isBusy(doctorId: string, startAt: string, ignoreId?: string) {
  if (doctorId === DOCTOR_IDS.truc) return true; // kịch bản: kín lịch cả kỳ
  return store.some(
    (item) =>
      item.id !== ignoreId &&
      item.doctor?.id === doctorId &&
      item.startAt === startAt &&
      (item.status === "BOOKED" || item.status === "CHECKED_IN"),
  );
}

function eligibleFor(appointment: MockAppointment): EligibleDoctor[] {
  if (appointment.id === SCENARIO_IDS.af1) return [];
  const date = appointment.startAt.slice(0, 10);
  const time = appointment.startAt.slice(11, 16);
  if (!shiftSlotTimes(date).includes(time)) return [];
  return DOCTORS.filter(
    (doctor) =>
      doctor.bookable &&
      doctor.id !== appointment.doctor?.id &&
      !isBusy(doctor.id, appointment.startAt, appointment.id),
  ).map(({ id, fullName }) => ({ id, fullName }));
}

/** GET /api/v1/admin/appointments/{id}/eligible-doctors */
export async function listEligibleDoctors(
  appointmentId: string,
  signal?: AbortSignal,
): Promise<ItemsResponse<EligibleDoctor>> {
  await latency(signal);
  const appointment = findOrFail(appointmentId);
  if (appointment.status !== "BOOKED") mockFail(409, "MSG-32");
  return { items: eligibleFor(appointment) };
}

/** POST /api/v1/admin/appointments/{id}/reassign */
export async function reassignAppointmentDoctor(
  appointmentId: string,
  doctorId: string,
): Promise<Appointment> {
  await latency();
  const appointment = findOrFail(appointmentId);
  if (!doctorId)
    mockFail(400, "MSG-12", undefined, [{ field: "doctorId", code: "MSG-12" }]);
  if (
    appointment.status !== "BOOKED" ||
    appointment.id === SCENARIO_IDS.changedMidway
  )
    mockFail(409, "MSG-32");
  const target = eligibleFor(appointment).find(
    (doctor) => doctor.id === doctorId,
  );
  if (
    !target ||
    (appointment.id === SCENARIO_IDS.slotLostOnPick &&
      doctorId === DOCTOR_IDS.minh)
  )
    mockFail(409, "MSG-24");
  appointment.doctor = target;
  appointment.allowedActions = ["reassign"];
  return publicView(appointment);
}

/** POST /api/v1/admin/appointments/{id}/notify-no-replacement */
export async function notifyNoReplacement(
  appointmentId: string,
): Promise<Appointment> {
  await latency();
  const appointment = findOrFail(appointmentId);
  if (
    appointment.status !== "BOOKED" ||
    appointment.id === SCENARIO_IDS.changedMidway ||
    !appointment.allowedActions.includes("notifyNoReplacement")
  )
    mockFail(409, "MSG-32");
  appointment.allowedActions = appointment.allowedActions.filter(
    (action) => action !== "notifyNoReplacement",
  );
  return publicView(appointment);
}

/** GET /api/v1/admin/doctors/{id}/availability?from=&to= */
export async function getDoctorAvailability(
  doctorId: string,
  range: { from: string; to: string },
  signal?: AbortSignal,
): Promise<DoctorAvailability> {
  await latency(signal);
  const doctor = DOCTORS.find((item) => item.id === doctorId);
  if (!doctor) mockFail(404, "MSG-50");
  if (!doctor.bookable)
    mockFail(409, "MSG-24", undefined, [{ field: "doctorId", code: "MSG-24" }]);
  if (doctor.id === DOCTOR_IDS.bao) mockFail(500, "MSG-04");
  if (range.from > range.to)
    mockFail(400, "MSG-54", undefined, [{ field: "to", code: "MSG-54" }]);
  if (range.to > addDays(range.from, 6))
    throw new ApiError(400, { detail: "Khoảng xem tối đa 7 ngày." });
  const days: AvailabilityDay[] = [];
  for (let date = range.from; date <= range.to; date = addDays(date, 1)) {
    const times = shiftSlotTimes(date);
    days.push({
      date,
      hasShift: times.length > 0,
      slots: times.map((time) => {
        const startAt = `${date}T${time}:00+07:00`;
        return {
          startAt,
          status: isBusy(doctor.id, startAt) ? "BOOKED" : "FREE",
        };
      }),
    });
  }
  return {
    doctorId,
    from: range.from,
    to: range.to,
    slotMinutes: SLOT_MINUTES,
    days,
  };
}

/** GET /api/v1/admin/doctors/lookup?bookable= */
export async function lookupDoctors(
  params: { bookable?: boolean } = {},
): Promise<ItemsResponse<DoctorLookupItem>> {
  await latency();
  return {
    items: clone(
      DOCTORS.filter(
        (doctor) =>
          params.bookable === undefined || doctor.bookable === params.bookable,
      ),
    ),
  };
}
