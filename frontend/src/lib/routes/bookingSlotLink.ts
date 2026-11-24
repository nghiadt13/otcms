import { PATIENT_ROUTES } from "./patientRoutes";

// Public doctor free-time links (UC16) open the booking page with the service, doctor, date and time
// already chosen: ?serviceId=&doctorId=&date=&time=. The booking page still re-checks that the time is
// open before preselecting it; malformed parameters are ignored.

/** Preselected slot from the URL. `doctorId` null means the clinic arranges the doctor. */
export interface BookingSlotQuery {
  serviceId: string;
  doctorId: string | null;
  /** YYYY-MM-DD, Vietnam time. */
  date: string;
  /** HH:mm, Vietnam time. */
  time: string;
}

/** Homepage booking starts with a service and a day; the patient chooses a real time in the wizard. */
export type BookingPreference =
  | BookingSlotQuery
  | {
      serviceId: string;
      date: string;
      time: null;
      doctorId: null;
    };

type SearchParamValue = string | string[] | undefined;

const ID_PATTERN = /^[A-Za-z0-9-]{1,64}$/;
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

/** Booking page path with the preselected slot; the doctor parameter is omitted when `doctorId` is null. */
export function bookingSlotHref(slot: BookingSlotQuery): string {
  const params = new URLSearchParams({ serviceId: slot.serviceId });
  if (slot.doctorId !== null) params.set("doctorId", slot.doctorId);
  params.set("date", slot.date);
  params.set("time", slot.time);
  return `${PATIENT_ROUTES.newAppointment}?${params.toString()}`;
}

/** Reads the booking page `searchParams`. A missing or malformed service, date or time yields null. */
export function readBookingSlotQuery(
  params: Record<string, SearchParamValue>,
): BookingSlotQuery | null {
  const serviceId = firstValue(params.serviceId);
  const date = firstValue(params.date);
  const time = firstValue(params.time);
  if (!serviceId || !ID_PATTERN.test(serviceId)) return null;
  if (!date || !isCalendarDate(date)) return null;
  if (!time || !TIME_PATTERN.test(time)) return null;
  // A malformed doctor is dropped on its own: the slot is still preselected and the doctor left open.
  const doctor = firstValue(params.doctorId);
  const doctorId = doctor && ID_PATTERN.test(doctor) ? doctor : null;
  return { serviceId, doctorId, date, time };
}

export function readBookingPreference(
  params: Record<string, SearchParamValue>,
): BookingPreference | null {
  if (params.time !== undefined || params.doctorId !== undefined)
    return readBookingSlotQuery(params);
  const { serviceId, date } = params;
  if (typeof serviceId !== "string" || !ID_PATTERN.test(serviceId)) return null;
  if (typeof date !== "string" || !isCalendarDate(date)) return null;
  return { serviceId, date, time: null, doctorId: null };
}

export function bookingPreferenceHref(preference: BookingPreference): string {
  if (preference.time !== null) return bookingSlotHref(preference);
  const query = new URLSearchParams({
    serviceId: preference.serviceId,
    date: preference.date,
  });
  return `${PATIENT_ROUTES.newAppointment}?${query}`;
}

function firstValue(value: SearchParamValue): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

// The date must exist (rejects 2026-02-30), not only match the YYYY-MM-DD shape.
function isCalendarDate(value: string): boolean {
  if (!DATE_PATTERN.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return (
    !Number.isNaN(parsed.getTime()) && parsed.toISOString().startsWith(value)
  );
}
