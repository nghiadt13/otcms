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
  getMyRescheduleSlots,
  listMyAppointments,
  rescheduleMyAppointment,
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

// Đồng hồ ở beforeEach: 31/10/2026 09:00 giờ VN. Hạn tự hủy/đổi lịch 120 phút (plan mục 3, A11).
describe("patientAppointmentLiveApi hạn tự hủy và đổi lịch 120 phút", () => {
  it("đã thanh toán còn đúng 120 phút: được đổi lịch, không được hủy", async () => {
    apiRequest.mockResolvedValueOnce({
      ...dto(true),
      startTime: "2026-10-31T11:00:00+07:00",
      patientRescheduleCount: 0,
      remainingReschedules: 2,
      maxPatientReschedules: 2,
    });
    const appointment = await getMyAppointment("apt-past");
    expect(appointment.status).toBe("BOOKED");
    expect(appointment.allowedActions).toEqual(["reschedule"]);
  });

  it("đã thanh toán hết 2 lượt đổi lịch: không còn nút đổi lịch", async () => {
    apiRequest.mockResolvedValueOnce({
      ...dto(true),
      startTime: "2026-10-31T12:00:00+07:00",
      patientRescheduleCount: 2,
      remainingReschedules: 0,
      maxPatientReschedules: 2,
    });
    const appointment = await getMyAppointment("apt-past");
    expect(appointment.allowedActions).toEqual([]);
  });

  it("đã thanh toán còn 119 phút: không có nút đổi lịch hay hủy, lịch vẫn Đã đặt", async () => {
    apiRequest.mockResolvedValueOnce({
      ...dto(true),
      startTime: "2026-10-31T10:59:00+07:00",
    });
    const appointment = await getMyAppointment("apt-past");
    expect(appointment.status).toBe("BOOKED");
    expect(appointment.allowedActions).toEqual([]);
  });

  it("chưa thanh toán còn đủ hạn: được hủy và trả online, không được đổi lịch", async () => {
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

describe("patientAppointmentLiveApi reschedule", () => {
  it("gọi GET reschedule-availability và PATCH reschedule đúng endpoint", async () => {
    const slot = {
      doctorId: "doctor-2",
      doctorDisplayName: "BS. Hai",
      startTime: "2026-11-01T09:00:00+07:00",
    };
    apiRequest.mockResolvedValueOnce({ slots: [slot] });
    const slots = await getMyRescheduleSlots("apt-1", "2026-11-01");
    expect(slots).toEqual([slot]);
    expect(apiRequest).toHaveBeenLastCalledWith(
      "/api/v1/appointments/apt-1/reschedule-availability?from=2026-11-01&to=2026-11-01",
      { signal: undefined },
    );

    apiRequest.mockResolvedValueOnce({
      ...dto(true),
      id: "apt-1",
      doctorId: "doctor-2",
      doctorDisplayName: "BS. Hai",
      startTime: "2026-11-01T09:00:00+07:00",
      patientRescheduleCount: 1,
      remainingReschedules: 1,
      maxPatientReschedules: 2,
    });
    const updated = await rescheduleMyAppointment("apt-1", {
      doctorId: "doctor-2",
      startTime: "2026-11-01T09:00:00+07:00",
    });
    expect(apiRequest).toHaveBeenLastCalledWith(
      "/api/v1/appointments/apt-1/reschedule",
      {
        method: "PATCH",
        body: JSON.stringify({
          doctorId: "doctor-2",
          startTime: "2026-11-01T09:00:00+07:00",
        }),
      },
    );
    expect(updated.remainingReschedules).toBe(1);
    expect(updated.allowedActions).toEqual(["reschedule"]);
  });
});
