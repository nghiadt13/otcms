import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest }));
import { listAdminAppointments } from "./appointmentAdminLiveApi";

const dto = (id: string, examFeePaid: boolean) => ({
  id,
  patientProfileId: "profile-1",
  patientName: "Người bệnh",
  patientPhone: null,
  serviceId: "service-1",
  serviceName: "Khám bệnh",
  servicePrice: 3000,
  doctorId: "doctor-1",
  doctorDisplayName: "Bác sĩ",
  status: "CONFIRMED",
  startTime: "2026-10-31T07:30:00+07:00",
  examFeePaid,
});

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-10-31T09:00:00+07:00"));
});
afterEach(() => vi.useRealTimers());

describe("admin appointment expiry projection", () => {
  it("shows expired unpaid appointments as cancelled", async () => {
    apiRequest.mockResolvedValueOnce({
      items: [dto("unpaid", false)], page: 0, size: 20, totalElements: 1, totalPages: 1,
    });
    const result = await listAdminAppointments({});
    expect(result.items[0]).toMatchObject({ status: "CANCELLED", requiresReception: false });
  });

  it("marks expired paid appointments for reception follow-up", async () => {
    apiRequest.mockResolvedValueOnce({
      items: [dto("paid", true)], page: 0, size: 20, totalElements: 1, totalPages: 1,
    });
    const result = await listAdminAppointments({});
    expect(result.items[0]).toMatchObject({ status: "BOOKED", requiresReception: true });
  });
});
