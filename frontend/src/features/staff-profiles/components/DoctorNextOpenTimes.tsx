import Link from "next/link";
import type { DoctorOpenDay } from "../types/publicDoctor";
import { dayMonthVi, weekdayShortVi } from "../utils/availabilityDayLabel";
import { DoctorOpenTimeChips } from "./DoctorOpenTimeChips";

// Compact free-time summary under a doctor card in the directory: up to 3 upcoming days, 3 times each.
// The full 7-day schedule is on the doctor page.
const DAYS_SHOWN = 3;
const TIMES_PER_DAY = 3;

export function DoctorNextOpenTimes({
  doctorId,
  days,
}: {
  doctorId: string;
  days: readonly DoctorOpenDay[];
}) {
  const openDays = days
    .filter((day) => day.times.length > 0)
    .slice(0, DAYS_SHOWN);
  if (openDays.length === 0)
    return (
      <p className="mt-4 text-base text-muted-foreground">
        Hết giờ trong 7 ngày tới.
      </p>
    );
  return (
    <div className="mt-4">
      <ul className="grid gap-3">
        {openDays.map((day) => (
          <li
            className="flex flex-col gap-2 sm:flex-row sm:items-center sm:gap-4"
            key={day.date}
          >
            <span className="w-24 shrink-0 text-sm font-medium tabular-nums">
              {weekdayShortVi(day.date)} {dayMonthVi(day.date)}
            </span>
            <DoctorOpenTimeChips
              date={day.date}
              doctorId={doctorId}
              times={day.times.slice(0, TIMES_PER_DAY)}
            />
          </li>
        ))}
      </ul>
      <Link
        className="mt-3 inline-block text-sm font-medium text-primary underline-offset-4 hover:underline"
        href={`/doctors/${encodeURIComponent(doctorId)}`}
      >
        Xem đủ lịch 7 ngày
      </Link>
    </div>
  );
}
