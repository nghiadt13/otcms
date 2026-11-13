import { beforeEach, expect, it, vi } from "vitest";
import {
  assessDoctorInterval,
  deleteDoctorInterval,
  listDoctorSchedule,
  saveDoctorInterval,
  scheduleRange,
  vietnamDateTime,
} from "./doctorScheduleApi";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
beforeEach(() => {
  request.mockReset();
});

it("uses inclusive Vietnam dates even across month boundaries", () => {
  expect(scheduleRange("2026-10-31", "2026-11-01")).toEqual({
    from: "2026-10-30T17:00:00.000Z",
    to: "2026-11-01T17:00:00.000Z",
  });
  expect(vietnamDateTime("2026-11-01T08:00")).toBe("2026-11-01T01:00:00.000Z");
});
it("uses the actual shift read, impact and write contracts", async () => {
  request.mockResolvedValue({ items: [] });
  await listDoctorSchedule("doctor", "shifts", "2026-11-01", "2026-11-07");
  expect(request.mock.calls[0][0]).toContain(
    "/api/v1/admin/doctors/doctor/shifts?",
  );
  const body = {
    startsAt: "2026-11-01T01:00:00Z",
    endsAt: "2026-11-01T02:00:00Z",
  };
  await assessDoctorInterval("doctor", "shifts", "shift", body);
  expect(JSON.parse(request.mock.calls[1][1].body)).toEqual({
    resourceType: "SHIFT",
    resourceId: "shift",
    ...body,
  });
  await saveDoctorInterval("doctor", "shifts", "shift", body);
  expect(request).toHaveBeenLastCalledWith(
    "/api/v1/admin/doctors/doctor/shifts/shift",
    expect.objectContaining({ method: "PUT", body: JSON.stringify(body) }),
  );
});

it("deletes a shift directly; the backend blocks it when bookings are affected", async () => {
  request.mockResolvedValue(undefined);
  await deleteDoctorInterval("d", "shifts", "shift");
  expect(request).toHaveBeenCalledTimes(1);
  expect(request.mock.calls[0]).toEqual([
    "/api/v1/admin/doctors/d/shifts/shift",
    { method: "DELETE" },
  ]);
});
