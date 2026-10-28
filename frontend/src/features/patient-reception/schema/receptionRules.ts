import type {
  PatientInput,
  Patient,
  PatientSearchQuery,
  Priority,
  ReceptionState,
  QueueEntry,
} from "../types";

export const clinicDate = (date = new Date()) =>
  new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date);
export function addDays(date: string, days: number) {
  return new Date(Date.parse(`${date}T00:00:00Z`) + days * 86400000)
    .toISOString()
    .slice(0, 10);
}
export function isDate(value: string) {
  return (
    /^\d{4}-\d{2}-\d{2}$/.test(value) &&
    Number.isFinite(Date.parse(value)) &&
    new Date(value).toISOString().slice(0, 10) === value
  );
}
const clinicTimeFormat = new Intl.DateTimeFormat("en-GB", {
  timeZone: "Asia/Ho_Chi_Minh",
  hourCycle: "h23",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
});
export function clinicTimestamp(now = new Date()) {
  return `${clinicDate(now)}T${clinicTimeFormat.format(now)}+07:00`;
}
export function isFutureSlot(date: string, time: string, now: string) {
  return (
    isDate(date) &&
    /^([01]\d|2[0-3]):[0-5]\d$/.test(time) &&
    Date.parse(`${date}T${time}:00+07:00`) > Date.parse(now)
  );
}
export function normalizePhone(value: string) {
  return value.replace(/[\s().-]/g, "").replace(/^\+84/, "0");
}
export const normalizeText = (value: string) =>
  value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLocaleLowerCase("vi")
    .replace(/\s+/g, " ")
    .trim();

export function matchesPatientSearch(
  patient: Patient,
  query: PatientSearchQuery,
) {
  const text = normalizeText(query.q);
  if (!/[\p{L}\p{N}]/u.test(text) || (query.dob && patient.dob !== query.dob))
    return false;
  const phone = normalizePhone(query.q.trim());
  const code = text.replace(/[\s-]/g, "");
  return (
    text
      .split(" ")
      .every((word) => normalizeText(patient.name).includes(word)) ||
    (/^\d+$/.test(phone) && normalizePhone(patient.phone).includes(phone)) ||
    normalizeText(patient.id).replace(/[\s-]/g, "").includes(code)
  );
}
export function validatePatient(input: PatientInput, today: string) {
  const errors: Partial<Record<keyof PatientInput, string>> = {};
  if (input.name.trim().length < 2 || input.name.trim().length > 120)
    errors.name = "Nhập họ tên từ 2 đến 120 ký tự.";
  if (!isDate(input.dob) || input.dob > today || input.dob < "1900-01-01")
    errors.dob = "Ngày sinh phải hợp lệ và không ở tương lai.";
  if (!/^0\d{9}$/.test(normalizePhone(input.phone)))
    errors.phone = "Nhập số điện thoại Việt Nam gồm 10 chữ số.";
  if (!input.address.trim()) errors.address = "Nhập địa chỉ liên hệ.";
  if (!["Nam", "Nữ", "Khác"].includes(input.sex))
    errors.sex = "Chọn giới tính.";
  if (
    input.nationalId &&
    !/^(\d{12}|[A-Za-z]\d{7,8})$/.test(input.nationalId.trim())
  )
    errors.nationalId = "Nhập 12 số định danh hoặc số hộ chiếu hợp lệ.";
  if (input.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.email))
    errors.email = "Email chưa hợp lệ.";
  return errors;
}
// Clinic priority configuration: emergency first, other entitled groups together, then normal.
export function priorityRank(priority: Priority) {
  return priority === "emergency" ? 0 : priority === "normal" ? 2 : 1;
}
export const queueSort = (a: QueueEntry, b: QueueEntry) =>
  a.position - b.position || a.joinedAt.localeCompare(b.joinedAt);
export function withinReturnWindow(first: string, today: string) {
  return today >= first && today <= addDays(first, 6);
}
export function patientBookingAt(
  state: ReceptionState,
  patientId: string,
  date: string,
  time: string,
) {
  return state.appointments.find(
    (a) =>
      a.patientId === patientId &&
      a.date === date &&
      a.time === time &&
      ["booked", "checked-in"].includes(a.status),
  );
}
export function availableDoctors(
  state: ReceptionState,
  date: string,
  time: string,
  serviceId: string,
  excludeAppointmentId?: string,
) {
  if (!isDate(date) || !/^\d{2}:\d{2}$/.test(time)) return [];
  return state.doctors.filter(
    (doctor) =>
      !doctor.absent &&
      state.rooms.some(
        (r) =>
          r.id === doctor.roomId &&
          r.available &&
          r.serviceIds.includes(serviceId),
      ) &&
      state.shifts.some(
        (shift) =>
          shift.staffId === doctor.id &&
          shift.date === date &&
          shift.start <= time &&
          shift.end > time &&
          (Number(time.slice(0, 2)) * 60 +
            Number(time.slice(3)) -
            (Number(shift.start.slice(0, 2)) * 60 +
              Number(shift.start.slice(3)))) %
            20 ===
            0,
      ) &&
      !state.appointments.some(
        (a) =>
          a.id !== excludeAppointmentId &&
          a.doctorId === doctor.id &&
          a.date === date &&
          a.time === time &&
          ["booked", "checked-in"].includes(a.status),
      ),
  );
}
export function onDuty(state: ReceptionState) {
  const day = state.clock.slice(0, 10),
    time = state.clock.slice(11, 16);
  return state.doctors.filter(
    (d) =>
      !d.absent &&
      state.rooms.some((r) => r.id === d.roomId && r.available) &&
      state.shifts.some(
        (s) =>
          s.staffId === d.id &&
          s.date === day &&
          s.start <= time &&
          s.end > time,
      ),
  );
}
export function latestRoundReady(state: ReceptionState, visitId: string) {
  const orders = state.orders.filter((o) => o.visitId === visitId);
  const round = Math.max(0, ...orders.map((o) => o.round));
  return orders
    .filter((o) => o.round === round && o.status !== "cancelled")
    .every((o) => o.status === "results");
}
