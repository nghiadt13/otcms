import { StatusBadge } from "@/components/admin/StatusBadge";
import type { DoctorOpenDay } from "../types/publicDoctor";
import { dayMonthVi, weekdayShortVi } from "../utils/availabilityDayLabel";
import { DoctorOpenTimeChips } from "./DoctorOpenTimeChips";

// The 7-day schedule on the doctor page: one row per day. Free times are links to booking;
// a day without free times shows "Hết giờ".
export function DoctorOpenTimesByDay({
  doctorId,
  days,
}: {
  doctorId: string;
  days: readonly DoctorOpenDay[];
}) {
  return (
    <ul className="divide-y divide-border overflow-hidden rounded-2xl border bg-card">
      {days.map((day) => (
        <li
          className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-start sm:justify-between sm:gap-6"
          key={day.date}
        >
          <span className="text-lg font-medium tabular-nums sm:min-w-32 sm:pt-1">
            {weekdayShortVi(day.date)} {dayMonthVi(day.date)}
          </span>
          {day.times.length > 0 ? (
            <DoctorOpenTimeChips
              date={day.date}
              doctorId={doctorId}
              times={day.times}
            />
          ) : (
            <StatusBadge className="h-7 px-3 text-sm" tone="neutral">
              Hết giờ
            </StatusBadge>
          )}
        </li>
      ))}
    </ul>
  );
}
