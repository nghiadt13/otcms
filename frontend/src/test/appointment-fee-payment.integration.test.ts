import { beforeEach, describe, expect, it } from "vitest";
import { createAppointment } from "@/features/appointments/api/bookingApi";
import { resetBookingMock } from "@/features/appointments/api/bookingMockStore";
import {
  cancelMyAppointment,
  getMyAppointment,
  resetPatientAppointmentMock,
} from "@/features/appointments/api/patientAppointmentApi";
import {
  createOnlinePayment,
  getPayableSource,
  getPayment,
  listPayableSources,
  resetPatientBillingMock,
} from "@/features/billing/api/patientBillingApi";
import {
  getLinkedProfileRecords,
  resetLinkedProfilesMock,
} from "@/lib/patient-scope/linkedProfilesMockStore";

// Luồng nối hai feature trên dữ liệu giả, đúng thứ tự người dùng: đặt lịch → khoản chờ thanh toán →
// trả VNPay → phí của lịch hẹn thành đã thanh toán. Đặt ở đây (không trong feature hay lib) vì test
// này nối `appointments` và `billing`; ranh giới ESLint chỉ áp cho feature và lib.
// Đồng hồ: thứ bảy 24/10/2026, 09:00 giờ VN. Lịch đặt: thứ ba 27/10/2026, 10:00.

const NOW = new Date("2026-10-24T09:00:00+07:00");
const SLOT = "2026-10-27T10:00:00+07:00";

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
  resetPatientBillingMock();
});

describe("đặt lịch → thanh toán → phí đã trả (dữ liệu giả, hai feature)", () => {
  it("lịch vừa đặt hiện trong khoản chờ; trả VNPay xong thì phí PAID ở lịch hẹn và hết chờ", async () => {
    const an = profileId("BN-000123");
    const booked = await createAppointment({
      patientId: an,
      serviceId: "svc-kham-tq",
      startAt: SLOT,
      doctorId: null,
    });

    const payable = await listPayableSources({ patientId: an });
    expect(payable.items).toContainEqual(
      expect.objectContaining({
        kind: "appointment",
        id: booked.id,
        pendingCount: 1,
        pendingAmount: booked.examFee.amount,
      }),
    );

    const [fee] = (await getPayableSource("appointment", booked.id)).items;
    expect(fee).toMatchObject({
      status: "PENDING_PAYMENT",
      amount: booked.examFee.amount,
    });

    const { paymentId, total, redirectUrl } = await createOnlinePayment({
      sourceKind: "appointment",
      sourceId: booked.id,
      itemIds: [fee.id],
    });
    expect(total).toBe(booked.examFee.amount);
    expect(redirectUrl).toBe(`/portal/payments/result?paymentId=${paymentId}`);

    const payment = await getPayment(paymentId);
    expect(payment).toMatchObject({
      status: "SUCCESS",
      total: 200000,
      sourceId: booked.id,
    });

    const appointment = await getMyAppointment(booked.id);
    expect(appointment.examFee).toEqual({ amount: 200000, status: "PAID" });
    expect(appointment.allowedActions).toEqual(["cancel"]);

    const after = await listPayableSources({ patientId: an });
    expect(after.items.map((item) => item.id)).not.toContain(booked.id);
    const paid = await getPayableSource("appointment", booked.id);
    expect(paid.items[0]).toMatchObject({
      status: "PAID",
      receipt: { paymentId, receiptNo: payment.receiptNo },
    });
  });

  it("lịch đặt rồi hủy trước khi trả: hết khoản chờ và không trả được (404 MSG-41)", async () => {
    const an = profileId("BN-000123");
    const booked = await createAppointment({
      patientId: an,
      serviceId: "svc-kham-tq",
      startAt: SLOT,
      doctorId: null,
    });
    await cancelMyAppointment(booked.id);

    const payable = await listPayableSources({ patientId: an });
    expect(payable.items.map((item) => item.id)).not.toContain(booked.id);
    await expect(
      createOnlinePayment({
        sourceKind: "appointment",
        sourceId: booked.id,
        itemIds: [`fee-${booked.id}`],
      }),
    ).rejects.toMatchObject({ status: 404, code: "MSG-41" });
  });
});
