import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import {
  getLinkedProfileRecords,
  resetLinkedProfilesMock,
} from "@/lib/patient-scope/linkedProfilesMockStore";
import { createAppointment, getOpenTimes } from "./bookingApi";
import { readStoredAppointments, resetBookingMock } from "./bookingMockStore";
import {
  cancelPendingHold,
  getMyAppointment,
  listMyAppointments,
  resetPatientAppointmentMock,
} from "./patientAppointmentApi";

// Kho giả phản chiếu hành vi mới của BE để demo/test không lệch live: đặt online dịch vụ có phí →
// AWAITING_PAYMENT kèm hạn 15 phút, ẩn khỏi mọi danh sách; hủy/hết hạn thì mất.

const NOW = new Date("2026-10-23T07:00:00+07:00");
const SERVICE = "svc-kham-tq"; // 200.000 ₫
const SLOT = "2026-10-27T10:00:00+07:00";
// Hồ sơ của tài khoản bệnh nhân đang đăng nhập (hold của hồ sơ này mới hiện ra ở chi tiết lịch).
let PATIENT = "";

const online = (startAt = SLOT) => ({
  patientId: PATIENT,
  serviceId: SERVICE,
  startAt,
  doctorId: null,
  purpose: "Đau gối",
  bookedOnline: true,
});

beforeEach(() => {
  resetLinkedProfilesMock();
  PATIENT = getLinkedProfileRecords()[0].id;
  resetBookingMock({ now: NOW });
  resetPatientAppointmentMock();
});

describe("kho giả: giữ giờ chờ thanh toán", () => {
  it("đặt online dịch vụ có phí → AWAITING_PAYMENT, hạn 15 phút, chưa vào danh sách lịch", async () => {
    const hold = await createAppointment(online());

    expect(hold.status).toBe("AWAITING_PAYMENT");
    expect(hold.paymentDeadline).toBe("2026-10-23T07:15:00+07:00");
    expect(readStoredAppointments().map((item) => item.id)).not.toContain(
      hold.id,
    );
    expect(
      readStoredAppointments({ includeHolds: true }).map((item) => item.id),
    ).toContain(hold.id);
  });

  it("lễ tân đặt hộ (không bookedOnline) → xác nhận ngay, không có hạn", async () => {
    const booked = await createAppointment({
      ...online(),
      bookedOnline: undefined,
    });

    expect(booked.status).toBe("BOOKED");
    expect(booked).not.toHaveProperty("paymentDeadline");
    expect(readStoredAppointments().map((item) => item.id)).toContain(booked.id);
  });

  it("giữ giờ chiếm giờ: người khác không đặt trùng bác sĩ - giờ đó biến khỏi giờ trống", async () => {
    const before = await getOpenTimes({ serviceId: SERVICE, patientId: PATIENT });
    const doctorsBefore = before.days
      .flatMap((day) => day.times)
      .find((time) => time.startAt === SLOT)?.doctors.length;

    await createAppointment(online());

    const after = await getOpenTimes({ serviceId: SERVICE, patientId: PATIENT });
    const doctorsAfter = after.days
      .flatMap((day) => day.times)
      .find((time) => time.startAt === SLOT)?.doctors.length;
    expect(doctorsAfter ?? 0).toBeLessThan(doctorsBefore ?? 0);
  });

  it("cách giờ khám dưới 15 phút → BOOKING_TOO_SOON; lễ tân đặt hộ không bị chặn", async () => {
    resetBookingMock({ now: new Date("2026-10-27T09:50:00+07:00") });

    const error = await createAppointment(online()).catch((cause) => cause);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ errorCode: "BOOKING_TOO_SOON" });

    await expect(
      createAppointment({ ...online(), bookedOnline: undefined }),
    ).resolves.toMatchObject({ status: "BOOKED" });
  });

  it("tạo lịch online mới tự hủy hold cũ", async () => {
    const first = await createAppointment(online());
    const second = await createAppointment(online("2026-10-27T11:00:00+07:00"));

    expect((await getMyAppointment(first.id)).status).toBe("PAYMENT_EXPIRED");
    expect((await getMyAppointment(second.id)).status).toBe("AWAITING_PAYMENT");
  });

  it("pending-hold/cancel: nhả hold, idempotent, giờ đặt lại được", async () => {
    const hold = await createAppointment(online());

    expect(await cancelPendingHold()).toEqual({
      cancelledCount: 1,
      alreadyPaid: false,
    });
    expect(await cancelPendingHold()).toEqual({
      cancelledCount: 0,
      alreadyPaid: false,
    });
    expect((await getMyAppointment(hold.id)).status).toBe("PAYMENT_EXPIRED");
    await expect(createAppointment(online())).resolves.toMatchObject({
      status: "AWAITING_PAYMENT",
    });
  });

  it("hết hạn tự nhả: trạng thái PAYMENT_EXPIRED và giờ đặt lại được", async () => {
    // Đồng hồ kho theo giờ hệ thống để đẩy thời gian đi 16 phút mà không dựng lại kho.
    vi.useFakeTimers({ toFake: ["Date"] });
    try {
      vi.setSystemTime(NOW);
      resetBookingMock();
      const hold = await createAppointment(online());
      expect((await getMyAppointment(hold.id)).status).toBe("AWAITING_PAYMENT");

      vi.setSystemTime(new Date("2026-10-23T07:16:00+07:00"));

      expect((await getMyAppointment(hold.id)).status).toBe("PAYMENT_EXPIRED");
      await expect(createAppointment(online())).resolves.toMatchObject({
        status: "AWAITING_PAYMENT",
      });
    } finally {
      vi.useRealTimers();
    }
  });

  it("lịch giữ giờ không bao giờ có trong danh sách của bệnh nhân", async () => {
    await createAppointment(online());

    const upcoming = await listMyAppointments({
      when: "upcoming",
      page: 0,
      size: 50,
    });
    const past = await listMyAppointments({ when: "past", page: 0, size: 50 });

    const statuses = [...upcoming.items, ...past.items].map(
      (item) => item.status,
    );
    expect(statuses).not.toContain("AWAITING_PAYMENT");
    expect(statuses).not.toContain("PAYMENT_EXPIRED");
  });
});
