import type { BookingSlotQuery } from "@/lib/routes/bookingSlotLink";
import type { OpenDay, OpenTime } from "../types/booking";
import { hhmmOfStart } from "./bookingTime";

// Matches a free-time link from the public doctor page against the currently open times.
// Pure function, no network calls. Any part that is gone (day, time, doctor) is not preselected;
// the user chooses it manually.

export interface PreselectOutcome {
  /** The link's day, when it is still inside the bookable range; null otherwise. */
  day: OpenDay | null;
  /** The open time matching `time` from the link; null when it is gone. */
  time: OpenTime | null;
  /** false when the link names a doctor who is no longer available at that time. */
  doctorAvailable: boolean;
}

export function resolvePreselectedSlot(
  days: readonly OpenDay[],
  slot: BookingSlotQuery,
): PreselectOutcome {
  const day = days.find((item) => item.date === slot.date) ?? null;
  const time = day?.times.find((item) => hhmmOfStart(item.startAt) === slot.time) ?? null;
  const doctorAvailable =
    slot.doctorId === null ||
    (time?.doctors.some((doctor) => doctor.id === slot.doctorId) ?? false);
  return { day, time, doctorAvailable };
}
