import { StatusBadge } from "@/components/admin/StatusBadge";
import type { DoctorOpenDay } from "../types/publicDoctor";
import { dayMonthVi, weekdayShortVi } from "../utils/availabilityDayLabel";
import { DoctorOpenTimeChips } from "./DoctorOpenTimeChips";

// The 7-day schedule on the doctor page: one card per day. Free times are links to booking;
// a day without free times shows "Hết giờ".
export function DoctorOpenTimesByDay({
  doctorId,
  days,
}: {
  doctorId: string;
  days: readonly DoctorOpenDay[];
}) {
  return (
    <ul className="grid grid-flow-col auto-cols-[minmax(8.5rem,1fr)] gap-3 overflow-x-auto pb-2 xl:grid-cols-7">
      {days.map((day) => (
        <li
          className="flex min-w-0 flex-col items-start gap-3 rounded-2xl border bg-card p-3"
          key={day.date}
        >
          <span className="font-semibold whitespace-nowrap tabular-nums">
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
