import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest }));
vi.mock("@/lib/auth/currentAccountApi", () => ({
  peekCurrentAccount: () => ({ displayName: "Người bệnh" }),
}));

import { getMyAppointment } from "./patientAppointmentLiveApi";

const dto = (examFeePaid: boolean) => ({
  id: "apt-past",
  patientProfileId: "profile-1",
  serviceId: "service-1",
  serviceName: "Khám bệnh",
  servicePrice: 3000,
  doctorId: "doctor-1",
  doctorDisplayName: "Bác sĩ",
  status: "CONFIRMED",
  startTime: "2026-10-31T07:30:00+07:00",
  endTime: "2026-10-31T08:00:00+07:00",
  examFeePaid,
  visitId: null,
  sourceVisitId: null,
});

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-10-31T09:00:00+07:00"));
});
afterEach(() => vi.useRealTimers());

describe("patientAppointmentLiveApi expired appointments", () => {
  it("marks expired unpaid appointments cancelled and removes actions", async () => {
    apiRequest.mockResolvedValueOnce(dto(false));
    const appointment = await getMyAppointment("apt-past");
    expect(appointment.status).toBe("CANCELLED");
    expect(appointment.allowedActions).toEqual([]);
  });

  it("keeps paid expired appointments for reception follow-up but removes actions", async () => {
    apiRequest.mockResolvedValueOnce(dto(true));
    const appointment = await getMyAppointment("apt-past");
    expect(appointment.status).toBe("BOOKED");
    expect(appointment.allowedActions).toEqual([]);
  });
});
