import { addDays, formatTime, todayIsoDate } from "@/lib/format/formatDate";
import type { DoctorOpenDay, DoctorOpenTime } from "../types/publicDoctor";

// Dates and clock times in Asia/Ho_Chi_Minh, the same zone the booking page uses for days and hours.

/** Vietnam calendar date (YYYY-MM-DD) of an instant. */
export const vnDateOf = (startAt: string): string =>
  todayIsoDate(new Date(startAt));

/** Vietnam clock time (HH:mm) of an instant. */
export const vnTimeOf = (startAt: string): string => formatTime(startAt);

/**
 * `dayCount` days starting today (Vietnam time). Days without a free time are kept with an empty list,
 * so the page can show them as "Hết giờ".
 */
export function openDaysFromToday(
  times: readonly DoctorOpenTime[],
  dayCount: number,
): DoctorOpenDay[] {
  const today = todayIsoDate();
  const days: DoctorOpenDay[] = Array.from(
    { length: dayCount },
    (_, offset) => ({
      date: addDays(today, offset),
      times: [],
    }),
  );
  const byDate = new Map(days.map((day) => [day.date, day]));
  for (const time of times)
    byDate.get(vnDateOf(time.startAt))?.times.push(time);
  return days;
}
