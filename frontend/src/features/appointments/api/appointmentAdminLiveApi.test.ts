import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";

const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest }));
import { listAdminAppointments, listAdminReassignmentDoctors, reassignAdminAppointment } from "./appointmentAdminLiveApi";

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
  apiRequest.mockReset();
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-10-31T09:00:00+07:00"));
});

it("offers reassignment only for future booked appointments", async () => {
  apiRequest.mockResolvedValue({ items: [{ ...dto("future", true), startTime: "2026-11-01T07:30:00+07:00" }], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  const result = await listAdminAppointments({});
  expect(result.items[0].allowedActions).toEqual(["reassign"]);
});

it("loads only doctors available at the original appointment time", async () => {
  apiRequest.mockResolvedValue({ slots: [
    { doctorId: "new", doctorDisplayName: "Bác sĩ mới", startTime: "2026-11-01T00:30:00Z" },
    { doctorId: "later", doctorDisplayName: "Bác sĩ khác", startTime: "2026-11-01T01:30:00Z" },
  ] });
  const result = await listAdminReassignmentDoctors("appt", "2026-11-01T07:30:00+07:00");
  expect(result.items).toEqual([{ id: "new", fullName: "Bác sĩ mới" }]);
  expect(apiRequest.mock.calls[0][0]).toContain("from=2026-11-01&to=2026-11-01");
});

it("posts the selected doctor and reason, preserving patient details", async () => {
  apiRequest.mockResolvedValue({ doctorId: "new", doctorDisplayName: "Bác sĩ mới", startTime: "2026-11-01T00:30:00Z", status: "CONFIRMED" });
  const appointment = { id: "appt", startAt: "2026-11-01T07:30:00+07:00", patientName: "Người bệnh", patientPhone: "0123", bookedForDependent: false, serviceName: "Khám", doctor: { id: "old", fullName: "Bác sĩ cũ" }, status: "BOOKED" as const, examFeePaid: true, allowedActions: ["reassign" as const] };
  const result = await reassignAdminAppointment(appointment, "new", "  Bác sĩ cũ vắng mặt  ");
  expect(result.doctor).toEqual({ id: "new", fullName: "Bác sĩ mới" });
  expect(result.patientName).toBe("Người bệnh");
  expect(JSON.parse(apiRequest.mock.calls[0][1].body)).toEqual({ doctorId: "new", reason: "Bác sĩ cũ vắng mặt" });
});

it("maps a slot lost during submission to the candidate refresh error", async () => {
  apiRequest.mockRejectedValue(new ApiError(409, { errorCode: "SLOT_UNAVAILABLE" }));
  const appointment = { id: "appt", startAt: "2026-11-01T07:30:00+07:00", patientName: "Người bệnh", bookedForDependent: false, serviceName: "Khám", doctor: null, status: "BOOKED" as const, examFeePaid: true, allowedActions: ["reassign" as const] };
  await expect(reassignAdminAppointment(appointment, "new", "Bác sĩ cũ vắng")).rejects.toMatchObject({ status: 409, code: "MSG-24" });
});
afterEach(() => vi.useRealTimers());

describe("admin appointment expiry projection", () => {
  it("preserves backend status for past appointments until backend changes it", async () => {
    apiRequest.mockResolvedValueOnce({
      items: [dto("unpaid", false)],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    const result = await listAdminAppointments({});
    expect(result.items[0]).toMatchObject({
      status: "BOOKED",
      requiresReception: true,
    });
  });

  it("marks expired paid appointments for reception follow-up", async () => {
    apiRequest.mockResolvedValueOnce({
      items: [dto("paid", true)],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    const result = await listAdminAppointments({});
    expect(result.items[0]).toMatchObject({
      status: "BOOKED",
      requiresReception: true,
    });
  });
});
