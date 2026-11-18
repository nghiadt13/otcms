import { ApiError } from "@/lib/api/apiError";

// Fake public backend for the public doctor pages. It answers the two calls the open-times API makes
// (the clinic-service catalog and the booking availability per service) at the `publicApiRequest`
// boundary, so component tests run the real API layer on top of it.

export interface FakeOpenSlot {
  serviceId: string;
  doctorId: string;
  /** UTC instant, in the same format the backend sends (e.g. "2026-11-19T02:30:00Z"). */
  startAt: string;
}

export interface FakePublicDoctorApiOptions {
  serviceIds: readonly string[];
  slots: readonly FakeOpenSlot[];
  /** The first N availability calls fail with HTTP 503, to exercise the error state. */
  failingAvailabilityCalls?: number;
}

export function fakePublicDoctorApi({
  serviceIds,
  slots,
  failingAvailabilityCalls = 0,
}: FakePublicDoctorApiOptions) {
  let failuresLeft = failingAvailabilityCalls;
  return async (path: string): Promise<unknown> => {
    if (path === "/api/v1/clinic-services")
      return { items: serviceIds.map((id) => ({ id, name: id, price: 0 })) };
    if (path.startsWith("/api/v1/appointments/availability?")) {
      if (failuresLeft > 0) {
        failuresLeft -= 1;
        throw new ApiError(503);
      }
      const serviceId = new URL(path, "http://fake.local").searchParams.get(
        "serviceId",
      );
      return {
        slots: slots
          .filter((slot) => slot.serviceId === serviceId)
          .map((slot) => ({
            serviceId: slot.serviceId,
            doctorId: slot.doctorId,
            doctorDisplayName: slot.doctorId,
            startTime: slot.startAt,
            endTime: slot.startAt,
            occupiedUntil: null,
          })),
      };
    }
    throw new Error(`unexpected public API path in test: ${path}`);
  };
}

/** A Vietnam wall-clock time as the backend's UTC instant string, e.g. ("2026-11-19", "09:30"). */
export function vnInstant(date: string, hhmm: string): string {
  return new Date(`${date}T${hhmm}:00+07:00`)
    .toISOString()
    .replace(".000Z", "Z");
}
