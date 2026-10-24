import { addDays } from "@/lib/format/formatDate";
import { dateOfStart, vnIsoAt, weekdayIndex } from "../schema/bookingTime";
import type { OpenDay, OpenTime } from "../types/booking";
import {
  ABSENT_TODAY_DOCTOR_ID,
  BOOKING_DOCTORS,
  FULL_DAY_OFFSET,
  HORIZON_DAYS,
  REFUSING_DAY_OFFSET,
  REFUSING_DOCTOR_ID,
  SHIFT_WINDOWS_MINUTES,
  SLOT_MINUTES,
  type BookingDoctor,
} from "./bookingMockCatalog";
import { isDoctorBusyAt, isStartTaken } from "./bookingMockStore";

// Quy tắc giờ trống của kho giả (BR-20): giờ nằm trong ca của bác sĩ, theo bước SLOT_MINUTES,
// bỏ giờ đã đặt, giờ đã qua và giờ đã bị giữ. Chỉ dùng trong lớp API giả.

const pad2 = (n: number) => String(n).padStart(2, "0");

/** Mọi giờ bắt đầu "HH:mm" có thể đặt trong một ngày làm việc (theo ca và bước giờ). */
export const SLOT_START_TIMES: readonly string[] = SHIFT_WINDOWS_MINUTES.flatMap(
  ([start, end]) => {
    const times: string[] = [];
    for (let minute = start; minute + SLOT_MINUTES <= end; minute += SLOT_MINUTES)
      times.push(`${pad2(Math.floor(minute / 60))}:${pad2(minute % 60)}`);
    return times;
  },
);

/** Ngày cuối được phép đặt, tính từ `today`. */
export const horizonEnd = (today: string): string =>
  addDays(today, HORIZON_DAYS - 1);

const isFullDay = (date: string, today: string): boolean =>
  date === addDays(today, FULL_DAY_OFFSET);

/** Bác sĩ có ca và đang nhận đặt mới vào ngày này (chưa xét giờ đã đặt). */
export function acceptsBookingsOn(
  doctor: BookingDoctor,
  date: string,
  today: string,
): boolean {
  if (!doctor.weekdays.includes(weekdayIndex(date))) return false;
  if (isFullDay(date, today)) return false;
  if (date === today && doctor.id === ABSENT_TODAY_DOCTOR_ID) return false;
  const refuses =
    doctor.id === REFUSING_DOCTOR_ID &&
    date === addDays(today, REFUSING_DAY_OFFSET);
  return !refuses;
}

/** Bác sĩ còn trống đúng giờ bắt đầu này: có ca, nhận đặt, và chưa có lịch hiệu lực. */
export function freeDoctorsAt(startAt: string, today: string): BookingDoctor[] {
  const date = dateOfStart(startAt);
  return BOOKING_DOCTORS.filter(
    (doctor) =>
      acceptsBookingsOn(doctor, date, today) &&
      !isDoctorBusyAt(doctor.id, startAt),
  );
}

/** Giờ trống của một ngày. Giờ không còn bác sĩ nào thì bỏ hẳn, không trả về. */
export function openTimesOn(date: string, today: string, now: Date): OpenTime[] {
  const nowMs = now.getTime();
  return SLOT_START_TIMES.flatMap((hhmm) => {
    const startAt = vnIsoAt(date, hhmm);
    if (Date.parse(startAt) < nowMs || isStartTaken(startAt)) return [];
    const doctors = freeDoctorsAt(startAt, today);
    if (doctors.length === 0) return [];
    return [
      {
        startAt,
        doctors: doctors.map(({ id, fullName }) => ({ id, fullName })),
      },
    ];
  });
}

/** Các ngày từ `from` đến `to` (gồm cả hai đầu), mỗi ngày kèm giờ trống. */
export function openDaysFrom(
  from: string,
  to: string,
  today: string,
  now: Date,
): OpenDay[] {
  const days: OpenDay[] = [];
  for (let date = from; date <= to; date = addDays(date, 1))
    days.push({ date, times: openTimesOn(date, today, now) });
  return days;
}
