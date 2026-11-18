import Link from "next/link";
import { bookingSlotHref } from "@/lib/routes/bookingSlotLink";
import type { DoctorOpenTime } from "../types/publicDoctor";
import { dayMonthVi } from "../utils/availabilityDayLabel";
import { vnTimeOf } from "../utils/doctorOpenDays";

// Free times of one day as links. Each link opens booking with this doctor, service, day and time
// already selected. Links cannot be nested, so callers render this outside any card-wide link.
export function DoctorOpenTimeChips({
  doctorId,
  date,
  times,
}: {
  doctorId: string;
  /** YYYY-MM-DD, Vietnam time. */
  date: string;
  times: readonly DoctorOpenTime[];
}) {
  return (
    <ul className="flex flex-wrap gap-2">
      {times.map((time) => {
        const clock = vnTimeOf(time.startAt);
        return (
          <li key={time.startAt}>
            <Link
              aria-label={`Đặt lịch lúc ${clock} ngày ${dayMonthVi(date)}`}
              className="inline-flex h-9 items-center rounded-full border border-primary/40 px-3.5 text-sm font-medium tabular-nums text-primary outline-none transition-colors hover:bg-primary hover:text-primary-foreground focus-visible:ring-3 focus-visible:ring-ring/50"
              href={bookingSlotHref({
                serviceId: time.serviceId,
                doctorId,
                date,
                time: clock,
              })}
            >
              {clock}
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
