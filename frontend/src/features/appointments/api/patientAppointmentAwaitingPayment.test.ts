import { beforeEach, describe, expect, it, vi } from "vitest";

// Ranh giới ngoài: HTTP. Hồ sơ đã gắn và tài khoản cũng đi qua ranh giới của chúng.
const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest }));
vi.mock("@/lib/patient-scope/linkedProfilesApi", () => ({
  listLinkedProfiles: async () => ({ items: [] }),
}));
vi.mock("@/lib/auth/currentAccountApi", () => ({
  peekCurrentAccount: () => ({ displayName: "Người bệnh" }),
}));
import * as liveApi from "./patientAppointmentLiveApi";
import { listMyAppointments } from "./patientAppointmentLiveApi";

const dto = (
  id: string,
  overrides: Partial<{
    bookingSource: string;
    status: string;
    servicePrice: number | null;
    examFeePaid: boolean | null;
    paymentDeadline: string | null;
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

const page = (items: ReturnType<typeof dto>[], totalElements = items.length) => ({
  items,
  page: 0,
  size: 10,
  totalElements,
  totalPages: 1,
});

beforeEach(() => apiRequest.mockReset());

// BE đã ẩn lịch giữ giờ chờ thanh toán (AWAITING_PAYMENT / PAYMENT_EXPIRED) khỏi mọi danh sách:
// FE không đoán, không đọc thêm trang nào, không trừ số lịch ẩn.
describe("danh sách lịch hẹn của bệnh nhân không còn suy luận 'chờ thanh toán'", () => {
  it("giữ nguyên mọi lịch BE trả, kể cả tự đặt chưa trả phí, và lấy totalItems thẳng từ BE", async () => {
    apiRequest.mockResolvedValue(
      page(
        [
          dto("online-unpaid"),
          dto("online-paid", { examFeePaid: true }),
          dto("counter-unpaid", { bookingSource: "RECEPTIONIST_ASSISTED" }),
          dto("online-free", { servicePrice: null, examFeePaid: null }),
        ],
        27,
      ),
    );

    const result = await listMyAppointments({
      when: "upcoming",
      page: 0,
      size: 10,
    });

    expect(result.items.map((item) => item.id)).toEqual([
      "online-unpaid",
      "online-paid",
      "counter-unpaid",
      "online-free",
    ]);
    expect(result.totalItems).toBe(27);
    expect(result.items[0].status).toBe("BOOKED");
  });

  it("chỉ một lời gọi GET /appointments (không còn lần đọc thứ hai để đếm lịch ẩn)", async () => {
    apiRequest.mockResolvedValue(page([dto("a")]));

    await listMyAppointments({
      when: "past",
      page: 2,
      size: 5,
      patientId: "profile-1",
    });

    expect(apiRequest).toHaveBeenCalledTimes(1);
    const [url] = apiRequest.mock.calls[0] as [string];
    expect(url).toContain("/api/v1/appointments?");
    expect(url).toContain("window=PAST");
    expect(url).toContain("page=2");
    expect(url).toContain("size=5");
    expect(url).toContain("patientProfileId=profile-1");
  });

  it("không còn các hàm đoán/đọc riêng lịch chờ thanh toán", () => {
    expect(liveApi).not.toHaveProperty("listAwaitingPaymentAppointments");
  });

  it("lịch đã hủy vẫn là Đã hủy", async () => {
    apiRequest.mockResolvedValue(
      page([dto("cancelled", { status: "CANCELLED" })]),
    );

    const result = await listMyAppointments({
      when: "past",
      page: 0,
      size: 10,
    });

    expect(result.items.map((item) => [item.id, item.status])).toEqual([
      ["cancelled", "CANCELLED"],
    ]);
    expect(result.totalItems).toBe(1);
  });
});

describe("chi tiết lịch đang giữ giờ", () => {
  it("AWAITING_PAYMENT: giữ trạng thái và hạn, không có thao tác nào", async () => {
    const deadline = "2099-01-01T07:15:00+07:00";
    apiRequest.mockResolvedValue(
      dto("hold", { status: "AWAITING_PAYMENT", paymentDeadline: deadline }),
    );

    const appointment = await liveApi.getMyAppointment("hold");

    expect(appointment.status).toBe("AWAITING_PAYMENT");
    expect(appointment.paymentDeadline).toBe(deadline);
    expect(appointment.allowedActions).toEqual([]);
  });

  it("PAYMENT_EXPIRED: giữ trạng thái, không có thao tác nào", async () => {
    apiRequest.mockResolvedValue(dto("old", { status: "PAYMENT_EXPIRED" }));

    const appointment = await liveApi.getMyAppointment("old");

    expect(appointment.status).toBe("PAYMENT_EXPIRED");
    expect(appointment.paymentDeadline).toBeNull();
    expect(appointment.allowedActions).toEqual([]);
  });
});
