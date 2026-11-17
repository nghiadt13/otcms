import { beforeEach, describe, expect, it, vi } from "vitest";

// Ranh giới ngoài: HTTP, hồ sơ đã gắn, tài khoản.
const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest }));
vi.mock("@/lib/patient-scope/linkedProfilesApi", () => ({
  listLinkedProfiles: async () => ({
    items: [{ id: "profile-1", fullName: "Nguyễn Văn An" }],
  }),
}));
vi.mock("@/lib/auth/currentAccountApi", () => ({
  peekCurrentAccount: () => ({ displayName: "Người bệnh" }),
}));

import { createAppointment, listBookableServices } from "./bookingLiveApi";

const created = (overrides: Record<string, unknown> = {}) => ({
  id: "apt-1",
  patientProfileId: "profile-1",
  serviceId: "svc-paid",
  doctorId: "doc-1",
  startTime: "2099-01-01T01:00:00Z",
  ...overrides,
});

const request = (serviceId = "svc-paid") => ({
  patientId: "profile-1",
  serviceId,
  startAt: "2099-01-01T08:00:00+07:00",
  doctorId: "doc-1",
  purpose: "Đau gối",
});

beforeEach(async () => {
  apiRequest.mockReset();
  apiRequest.mockResolvedValueOnce({
    items: [
      { id: "svc-paid", name: "Khám cột sống", description: null, price: 250000 },
      { id: "svc-free", name: "Khám miễn phí", description: null, price: 0 },
    ],
  });
  await listBookableServices();
  apiRequest.mockReset();
});

describe("createAppointment (live): lịch đặt luôn đã xác nhận, phí trả sau", () => {
  it("CONFIRMED có phí: lịch đã đặt, phí còn chờ trả (bước thanh toán tùy chọn)", async () => {
    apiRequest.mockResolvedValue(created({ status: "CONFIRMED" }));

    const booked = await createAppointment(request());

    expect(booked.status).toBe("BOOKED");
    expect(booked.examFee).toEqual({ amount: 250000, status: "PENDING_PAYMENT" });
    expect(booked.patient.fullName).toBe("Nguyễn Văn An");
    expect(booked).not.toHaveProperty("paymentDeadline");
  });

  it("CONFIRMED (dịch vụ miễn phí): không thu phí", async () => {
    apiRequest.mockResolvedValue(
      created({ serviceId: "svc-free", status: "CONFIRMED" }),
    );

    const booked = await createAppointment(request("svc-free"));

    expect(booked.status).toBe("BOOKED");
    expect(booked.examFee).toEqual({ amount: 0, status: "NOT_REQUIRED" });
  });

  it("không gửi cờ bookedOnline lên BE (BE tự biết nguồn đặt từ tài khoản)", async () => {
    apiRequest.mockResolvedValue(created({ status: "CONFIRMED" }));

    await createAppointment({ ...request(), bookedOnline: true });

    const [, init] = apiRequest.mock.calls[0] as [string, RequestInit];
    expect(JSON.parse(init.body as string)).not.toHaveProperty("bookedOnline");
  });
});
