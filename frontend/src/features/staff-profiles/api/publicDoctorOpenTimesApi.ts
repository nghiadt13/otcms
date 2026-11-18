import { publicApiRequest } from "@/lib/api/client";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import type { DoctorOpenTime } from "../types/publicDoctor";

// Free times of all public doctors for the next 7 days (UC16 steps 2 and 4, BR-20).
// The backend has no endpoint grouped by doctor, and slots are computed per bookable service. So this
// reads the existing booking availability API once per public service and groups the slots by doctor.
// Slot rules (shifts, existing bookings, notice time) stay in the backend; nothing is recomputed here.

/** Length of the window shown on the public doctor pages, starting today (Vietnam time). */
export const OPEN_TIME_WINDOW_DAYS = 7;

/** One service in `GET /api/v1/clinic-services` (only the id is used). */
interface ClinicServiceItem {
  id: string;
}

/** One slot in `GET /api/v1/appointments/availability` (only the fields used here). */
export interface AvailabilitySlotItem {
  serviceId: string;
  doctorId: string;
  startTime: string;
}

/**
 * Groups slots by doctor, in time order. The same doctor at the same instant offered by several
 * services is kept once, under the first service in the list.
 */
export function groupOpenTimesByDoctor(
  slots: readonly AvailabilitySlotItem[],
): Map<string, DoctorOpenTime[]> {
  const seen = new Set<string>();
  const byDoctor = new Map<string, DoctorOpenTime[]>();
  for (const slot of slots) {
    const key = `${slot.doctorId}|${Date.parse(slot.startTime)}`;
    if (seen.has(key)) continue;
    seen.add(key);
    const times = byDoctor.get(slot.doctorId) ?? [];
    times.push({ serviceId: slot.serviceId, startAt: slot.startTime });
    byDoctor.set(slot.doctorId, times);
  }
  for (const times of byDoctor.values())
    times.sort((a, b) => Date.parse(a.startAt) - Date.parse(b.startAt));
  return byDoctor;
}

/**
 * Loads the free times of every public doctor for today plus the next 6 days. If any service call
 * fails the whole load fails: a failed call is not evidence that a doctor is fully booked.
 */
export async function listPublicDoctorOpenTimes(
  signal?: AbortSignal,
): Promise<Map<string, DoctorOpenTime[]>> {
  const { items } = await publicApiRequest<{ items: ClinicServiceItem[] }>(
    "/api/v1/clinic-services",
    { signal },
  );
  const from = todayIsoDate();
  const to = addDays(from, OPEN_TIME_WINDOW_DAYS - 1);
  const responses = await Promise.all(
    items.map((service) => {
      const params = new URLSearchParams({ serviceId: service.id, from, to });
      return publicApiRequest<{ slots: AvailabilitySlotItem[] }>(
        `/api/v1/appointments/availability?${params.toString()}`,
        { signal },
      );
    }),
  );
  return groupOpenTimesByDoctor(
    responses.flatMap((response) => response.slots),
  );
}
