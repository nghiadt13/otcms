import { beforeEach, describe, expect, it, vi } from "vitest";

const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest }));
vi.mock("@/lib/patient-scope/linkedProfilesApi", () => ({
  listLinkedProfiles: async () => ({ items: [] }),
}));
vi.mock("@/lib/auth/currentAccountApi", () => ({
  peekCurrentAccount: () => ({ displayName: "Người bệnh" }),
}));
import {
  listAwaitingPaymentAppointments,
  listMyAppointments,
} from "./patientAppointmentLiveApi";

const dto = (
  id: string,
  overrides: Partial<{
    bookingSource: string;
    status: string;
    servicePrice: number | null;
    examFeePaid: boolean | null;
  }> = {},
) => ({
  id,
  patientProfileId: "profile-1",
  serviceId: "service-1",
  serviceName: "Khám bệnh",
  servicePrice: 10000,
  doctorId: "doctor-1",
  doctorDisplayName: "Bác sĩ",
  status: "CONFIRMED",
  bookingSource: "PATIENT_ONLINE",
  startTime: "2099-01-01T07:30:00+07:00",
  endTime: "2099-01-01T08:00:00+07:00",
  examFeePaid: false,
  visitId: null,
  sourceVisitId: null,
  ...overrides,
});

const page = (items: ReturnType<typeof dto>[]) => ({
  items,
  page: 0,
  size: 10,
  totalElements: items.length,
  totalPages: 1,
});

beforeEach(() => apiRequest.mockReset());

// Lịch tự đặt chưa thanh toán xong chưa được xác nhận: không hiện trong Lịch hẹn.
describe("lịch tự đặt chưa thanh toán xong", () => {
  const items = [
    dto("online-unpaid"),
    dto("online-paid", { examFeePaid: true }),
    dto("counter-unpaid", { bookingSource: "RECEPTIONIST_ASSISTED" }),
    dto("followup-unpaid", { bookingSource: "DOCTOR_FOLLOW_UP" }),
    dto("online-free", { servicePrice: null, examFeePaid: null }),
  ];

  it("không hiện trong danh sách và không tính vào tổng", async () => {
    apiRequest.mockResolvedValue(page(items));

    const result = await listMyAppointments({
      when: "upcoming",
      page: 0,
      size: 10,
    });

    expect(result.items.map((item) => item.id)).toEqual([
      "online-paid",
      "counter-unpaid",
      "followup-unpaid",
      "online-free",
    ]);
    expect(result.totalItems).toBe(4);
  });

  it("lịch đã hủy không bị coi là chờ thanh toán", async () => {
    apiRequest.mockResolvedValue(
      page([dto("cancelled", { status: "CANCELLED" })]),
    );

    const result = await listMyAppointments({
      when: "past",
      page: 0,
      size: 10,
    });

    expect(result.items.map((item) => item.id)).toEqual(["cancelled"]);
    expect(result.totalItems).toBe(1);
  });

  it("được liệt kê riêng để thanh toán tiếp hoặc hủy", async () => {
    apiRequest.mockResolvedValue(page(items));

    const awaiting = await listAwaitingPaymentAppointments();

    expect(awaiting.map((item) => item.id)).toEqual(["online-unpaid"]);
    expect(awaiting[0].allowedActions).toEqual(["cancel", "payExamFee"]);
  });
});
