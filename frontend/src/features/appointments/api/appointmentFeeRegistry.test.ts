import { beforeEach, describe, expect, it } from "vitest";
import {
  findMockAppointmentFee,
  markMockAppointmentFeePaid,
} from "@/lib/mock/sharedAppointmentFeeMock";
import {
  getLinkedProfileRecords,
  resetLinkedProfilesMock,
} from "@/lib/patient-scope/linkedProfilesMockStore";
import { createAppointment } from "./bookingApi";
import { readStoredAppointments, resetBookingMock } from "./bookingMockStore";
import {
  cancelMyAppointment,
  getMyAppointment,
  resetPatientAppointmentMock,
} from "./patientAppointmentApi";
import { RACE_APPOINTMENT_ID } from "./patientAppointmentMockData";

// Kho phí khám dùng chung (lib/mock/sharedAppointmentFeeMock.ts) do kho đặt lịch ghi: lúc nạp,
// khi đặt mới và khi hủy. Thanh toán đọc và đánh dấu đã trả ở đó; khu bệnh nhân và khu Lễ tân đọc
// trạng thái phí từ đó. Đồng hồ: thứ bảy 24/10/2026, 09:00 giờ VN.
const NOW = new Date("2026-10-24T09:00:00+07:00");

function profileId(patientCode: string): string {
  const profile = getLinkedProfileRecords().find(
    (item) => item.patientCode === patientCode,
  );
  if (!profile) throw new Error(`thiếu hồ sơ ${patientCode}`);
  return profile.id;
}

beforeEach(() => {
  resetLinkedProfilesMock();
  resetBookingMock({ now: NOW });
  resetPatientAppointmentMock();
});

describe("kho phí khám dùng chung: nạp dữ liệu ban đầu", () => {
  it("lịch của hồ sơ đã gắn có phí, tên, quan hệ và trạng thái lịch", () => {
    expect(findMockAppointmentFee("apt-1001")).toEqual({
      appointmentId: "apt-1001",
      profileId: profileId("BN-000123"),
      patientName: "Nguyễn Văn An",
      relationship: "SELF",
      startAt: expect.stringMatching(/\+07:00$/),
      serviceName: "Khám chỉnh hình tổng quát",
      examFeeAmount: 200000,
      examFeeStatus: "PENDING_PAYMENT",
      appointmentStatus: "BOOKED",
    });
    expect(findMockAppointmentFee("apt-1003")).toMatchObject({
      profileId: profileId("BN-000456"),
      relationship: "PARENT",
      examFeeStatus: "PAID",
    });
    expect(findMockAppointmentFee("apt-1002")?.appointmentStatus).toBe(
      "CANCELLED",
    );
  });

  it("lịch riêng khu bệnh nhân của hồ sơ đã gắn cũng có trong kho", () => {
    expect(findMockAppointmentFee(RACE_APPOINTMENT_ID)).toMatchObject({
      profileId: profileId("BN-000123"),
      appointmentStatus: "BOOKED",
      examFeeStatus: "PENDING_PAYMENT",
    });
    expect(findMockAppointmentFee("apt-2101")).toMatchObject({
      profileId: profileId("BN-000789"),
      appointmentStatus: "BOOKED",
    });
  });

  it("lịch của người do lễ tân quản lý (không gắn tài khoản) không vào kho", () => {
    expect(findMockAppointmentFee("apt-1004")).toBeUndefined();
    expect(findMockAppointmentFee("apt-1005")).toBeUndefined();
  });
});

describe("kho phí khám dùng chung: đặt mới, thanh toán, hủy", () => {
  it("đặt lịch mới thì có dòng phí chưa trả; người không gắn tài khoản thì không có dòng", async () => {
    const an = profileId("BN-000123");
    const booked = await createAppointment({
      patientId: an,
      serviceId: "svc-kham-tq",
      startAt: "2026-10-27T10:00:00+07:00",
      doctorId: null,
    });
    expect(findMockAppointmentFee(booked.id)).toMatchObject({
      profileId: an,
      examFeeAmount: 200000,
      examFeeStatus: "PENDING_PAYMENT",
      appointmentStatus: "BOOKED",
    });

    const binh = await createAppointment({
      patientId: "pat-binh",
      serviceId: "svc-kham-tq",
      startAt: "2026-10-27T11:00:00+07:00",
      doctorId: null,
    });
    expect(findMockAppointmentFee(binh.id)).toBeUndefined();
  });

  it("thanh toán đã ghi vào kho: lịch chưa trả thành PAID, danh sách và chi tiết đều thấy; chỉ còn Hủy", async () => {
    markMockAppointmentFeePaid("apt-1001");

    expect(
      readStoredAppointments().find((item) => item.id === "apt-1001")?.examFee
        .status,
    ).toBe("PAID");
    const item = await getMyAppointment("apt-1001");
    expect(item.examFee).toEqual({ amount: 200000, status: "PAID" });
    expect(item.allowedActions).toEqual(["cancel"]);
  });

  it("lịch riêng khu bệnh nhân đã trả (apt-race) cũng thành PAID ở chi tiết", async () => {
    markMockAppointmentFeePaid(RACE_APPOINTMENT_ID);
    const item = await getMyAppointment(RACE_APPOINTMENT_ID);
    expect(item.examFee.status).toBe("PAID");
    expect(item.allowedActions).toEqual(["cancel"]);
  });

  it("hủy lịch chưa trả: kho ghi Đã hủy, phí vẫn chưa trả nhưng không còn là khoản phải trả", async () => {
    await cancelMyAppointment("apt-1001");
    expect(findMockAppointmentFee("apt-1001")).toMatchObject({
      appointmentStatus: "CANCELLED",
      examFeeStatus: "PENDING_PAYMENT",
    });
  });

  it("hủy lịch riêng khu bệnh nhân (apt-2101): kho ghi Đã hủy", async () => {
    await cancelMyAppointment("apt-2101");
    expect(findMockAppointmentFee("apt-2101")?.appointmentStatus).toBe(
      "CANCELLED",
    );
  });

  it("hủy lịch đã trả: phí giữ là đã trả (không hoàn tiền)", async () => {
    await cancelMyAppointment("apt-1003");
    expect(findMockAppointmentFee("apt-1003")).toMatchObject({
      appointmentStatus: "CANCELLED",
      examFeeStatus: "PAID",
    });
  });

  it("reset kho đặt lịch thì trạng thái thanh toán trở về dữ liệu ban đầu", () => {
    markMockAppointmentFeePaid("apt-1001");
    resetBookingMock({ now: NOW });
    expect(findMockAppointmentFee("apt-1001")?.examFeeStatus).toBe(
      "PENDING_PAYMENT",
    );
  });
});
