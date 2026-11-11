import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest }));
// Đồng hồ giả không chạy độ trễ của kho giả, nên trả thẳng danh sách hồ sơ đã gắn.
vi.mock("@/lib/patient-scope/linkedProfilesApi", () => ({
  listLinkedProfiles: async () => ({
    items: [
      {
        id: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f02",
        patientCode: "",
        fullName: "Nguyễn Thị Lan",
        dateOfBirth: "1936-03-14",
        sex: "FEMALE",
        relationship: "PARENT",
      },
    ],
  }),
}));
vi.mock("@/lib/auth/currentAccountApi", () => ({
  peekCurrentAccount: () => ({ displayName: "Người bệnh" }),
}));

import {
  getMyAppointment,
  listMyAppointments,
} from "./patientAppointmentLiveApi";

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

describe("patientAppointmentLiveApi listMyAppointments", () => {
  const emptyPage = {
    items: [],
    page: 0,
    size: 10,
    totalElements: 0,
    totalPages: 0,
  };

  it("gửi patientProfileId khi lọc theo người khám", async () => {
    apiRequest.mockResolvedValueOnce(emptyPage);
    await listMyAppointments({
      when: "upcoming",
      patientId: "profile-2",
      page: 0,
      size: 10,
    });
    const url = apiRequest.mock.calls.at(-1)![0] as string;
    expect(url).toContain("patientProfileId=profile-2");
    expect(url).toContain("window=UPCOMING");
  });

  it("ghi tên và quan hệ theo hồ sơ của lịch, không gán hết cho tài khoản", async () => {
    apiRequest.mockResolvedValueOnce({
      ...emptyPage,
      items: [
        {
          ...dto(false),
          id: "apt-parent",
          patientProfileId: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f02",
          startTime: "2026-11-20T07:30:00+07:00",
        },
        {
          ...dto(false),
          id: "apt-unknown",
          patientProfileId: "profile-gone",
          patientName: "Người đã gỡ",
          startTime: "2026-11-20T08:30:00+07:00",
        },
      ],
    });
    const { items } = await listMyAppointments({
      when: "upcoming",
      page: 0,
      size: 10,
    });
    expect(items[0].patient).toMatchObject({
      fullName: "Nguyễn Thị Lan",
      relationship: "PARENT",
    });
    expect(items[1].patient.fullName).toBe("Người đã gỡ");
  });

  it("không gửi patientProfileId khi chọn tất cả người khám", async () => {
    apiRequest.mockResolvedValueOnce(emptyPage);
    await listMyAppointments({ when: "past", page: 0, size: 10 });
    expect(apiRequest.mock.calls.at(-1)![0] as string).not.toContain(
      "patientProfileId",
    );
  });
});

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

// Đồng hồ ở beforeEach: 31/10/2026 09:00 giờ VN. Hạn tự hủy 120 phút (plan mục 3, A11).
describe("patientAppointmentLiveApi hạn tự hủy 120 phút", () => {
  it("còn đúng 120 phút: được hủy", async () => {
    apiRequest.mockResolvedValueOnce({
      ...dto(true),
      startTime: "2026-10-31T11:00:00+07:00",
    });
    const appointment = await getMyAppointment("apt-past");
    expect(appointment.status).toBe("BOOKED");
    expect(appointment.allowedActions).toEqual(["cancel"]);
  });

  it("còn 119 phút: không có nút hủy, lịch vẫn Đã đặt", async () => {
    apiRequest.mockResolvedValueOnce({
      ...dto(true),
      startTime: "2026-10-31T10:59:00+07:00",
    });
    const appointment = await getMyAppointment("apt-past");
    expect(appointment.status).toBe("BOOKED");
    expect(appointment.allowedActions).toEqual([]);
  });

  it("chưa thanh toán còn đủ hạn: được hủy và trả online", async () => {
    apiRequest.mockResolvedValueOnce({
      ...dto(false),
      startTime: "2026-10-31T12:00:00+07:00",
    });
    const appointment = await getMyAppointment("apt-past");
    expect(appointment.allowedActions).toEqual(["cancel", "payExamFee"]);
  });

  it("chưa thanh toán còn dưới hạn: không hủy được nhưng vẫn trả online", async () => {
    apiRequest.mockResolvedValueOnce({
      ...dto(false),
      startTime: "2026-10-31T10:30:00+07:00",
    });
    const appointment = await getMyAppointment("apt-past");
    expect(appointment.allowedActions).toEqual(["payExamFee"]);
  });
});
