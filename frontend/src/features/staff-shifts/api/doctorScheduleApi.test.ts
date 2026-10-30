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
it("uses the actual time-off read and write contracts", async () => {
  request.mockResolvedValue({ items: [] });
  await listDoctorSchedule("doctor", "time-offs", "2026-11-01", "2026-11-07");
  expect(request.mock.calls[0][0]).toContain(
    "/api/v1/admin/doctor-profiles/doctor/time-offs?",
  );
  const body = {
    startsAt: "2026-11-01T01:00:00Z",
    endsAt: "2026-11-01T02:00:00Z",
  };
  await assessDoctorInterval("doctor", "time-offs", "off", body);
  expect(JSON.parse(request.mock.calls[1][1].body)).toEqual({
    resourceType: "TIME_OFF",
    resourceId: "off",
    ...body,
  });
  await saveDoctorInterval("doctor", "time-offs", "off", body);
  expect(request).toHaveBeenLastCalledWith(
    "/api/v1/admin/doctors/doctor/time-offs/off",
    expect.objectContaining({ method: "PUT", body: JSON.stringify(body) }),
  );
});

it("checks time-off removal impact before deleting and never deletes when blocked", async () => {
  request.mockResolvedValue({
    blocked: true,
    affectedBookingCount: 1,
    activeCareBlocks: false,
  });
  const result = await deleteDoctorInterval("d", "time-offs", "off").catch(
    (e) => ({ code: e.code }),
  );
  expect(result).toEqual({ code: "MSG-36" });
  expect(request).toHaveBeenCalledTimes(1);
  expect(request.mock.calls[0][0]).toBe(
    "/api/v1/admin/doctors/d/time-offs/off/mutation-impact",
  );
  request.mockReset();
  request
    .mockResolvedValueOnce({ blocked: false })
    .mockResolvedValueOnce(undefined);
  await deleteDoctorInterval("d", "time-offs", "off");
  expect(request.mock.calls[1]).toEqual([
    "/api/v1/admin/doctors/d/time-offs/off",
    { method: "DELETE" },
  ]);
});
