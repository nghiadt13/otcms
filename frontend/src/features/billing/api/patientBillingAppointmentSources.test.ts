import { beforeEach, describe, expect, it } from "vitest";
import {
  findMockAppointmentFee,
  resetMockAppointmentFees,
  upsertMockAppointmentFee,
  type MockAppointmentFee,
} from "@/lib/mock/sharedAppointmentFeeMock";
import {
  createOnlinePayment,
  getPayableSource,
  getPayment,
  listPayableSources,
  resetPatientBillingMock,
} from "./patientBillingApi";
import { appointmentFeeItemId } from "./patientBillingAppointmentSources";
import { SEED_PATIENTS } from "./patientBillingMockData";

// Nguồn lịch hẹn lấy từ kho phí khám dùng chung (kho đặt lịch ghi vào đó). Các test này nạp kho
// trực tiếp, không gọi feature đặt lịch: billing không import feature khác.

const AN = SEED_PATIENTS.an;
const LAN = SEED_PATIENTS.lan;

function fee(
  overrides: Partial<MockAppointmentFee> & Pick<MockAppointmentFee, "appointmentId">,
): MockAppointmentFee {
  return {
    profileId: AN.id,
    patientName: AN.fullName,
    relationship: "SELF",
    startAt: "2026-10-27T10:00:00+07:00",
    serviceName: "Khám chỉnh hình tổng quát",
    examFeeAmount: 200000,
    examFeeStatus: "PENDING_PAYMENT",
    appointmentStatus: "BOOKED",
    ...overrides,
  };
}

beforeEach(() => {
  resetPatientBillingMock();
  resetMockAppointmentFees();
  upsertMockAppointmentFee(fee({ appointmentId: "apt-pay-an" }));
  upsertMockAppointmentFee(
    fee({
      appointmentId: "apt-paid-lan",
      profileId: LAN.id,
      patientName: LAN.fullName,
      relationship: "PARENT",
      examFeeStatus: "PAID",
    }),
  );
  upsertMockAppointmentFee(fee({ appointmentId: "apt-cancel-an", appointmentStatus: "CANCELLED" }));
  upsertMockAppointmentFee(
    fee({ appointmentId: "apt-stranger", profileId: "pat-binh", patientName: "Trần Thị Bình" }),
  );
});

describe("nguồn lịch hẹn trong danh sách chờ thanh toán", () => {
  it("chỉ lịch Đã đặt, phí chưa trả, của hồ sơ đã gắn; khoản đã trả không tính vào số chờ", async () => {
    const { items } = await listPayableSources();
    const ids = items.map((item) => item.id);
    expect(ids).toContain("apt-pay-an");
    expect(ids).not.toContain("apt-cancel-an");
    expect(ids).not.toContain("apt-stranger");
    expect(ids).not.toContain("apt-paid-lan");

    expect(items.find((item) => item.id === "apt-pay-an")).toMatchObject({
      kind: "appointment",
      date: "2026-10-27T10:00:00+07:00",
      patient: { id: AN.id, fullName: AN.fullName, relationship: "SELF" },
      pendingCount: 1,
      pendingAmount: 200000,
    });
  });

  it("lọc theo hồ sơ người khám áp dụng cho lịch hẹn", async () => {
    const { items } = await listPayableSources({ patientId: AN.id });
    expect(items.map((item) => item.id)).toContain("apt-pay-an");
    expect(items.some((item) => item.patient.id !== AN.id)).toBe(false);
  });

  it("lịch đã có trong kho riêng (apt-lan-27) chỉ hiện một lần, với khoản của kho riêng", async () => {
    upsertMockAppointmentFee(
      fee({ appointmentId: "apt-lan-27", profileId: LAN.id, patientName: LAN.fullName, relationship: "PARENT" }),
    );
    const { items } = await listPayableSources();
    expect(items.filter((item) => item.id === "apt-lan-27")).toHaveLength(1);
    const detail = await getPayableSource("appointment", "apt-lan-27");
    expect(detail.items.map((item) => item.id)).toEqual(["fee-lan-27"]);
  });
});

describe("chi tiết và trả tiền lịch hẹn", () => {
  it("một khoản phí khám mang tên dịch vụ; khoản đã trả có trạng thái đã trả", async () => {
    const detail = await getPayableSource("appointment", "apt-pay-an");
    expect(detail.items).toEqual([
      {
        id: appointmentFeeItemId("apt-pay-an"),
        label: "Phí khám · Khám chỉnh hình tổng quát",
        amount: 200000,
        status: "PENDING_PAYMENT",
        cancellationReason: null,
        receipt: null,
      },
    ]);

    const paid = await getPayableSource("appointment", "apt-paid-lan");
    expect(paid.items[0]).toMatchObject({ status: "PAID", amount: 200000 });
  });

  it("trả thành công: khoản thành PAID trong kho phí chung, biên lai gắn với giao dịch, nguồn hết chờ", async () => {
    const itemId = appointmentFeeItemId("apt-pay-an");
    const { paymentId, total } = await createOnlinePayment({
      sourceKind: "appointment",
      sourceId: "apt-pay-an",
      itemIds: [itemId],
    });
    expect(total).toBe(200000);

    const payment = await getPayment(paymentId);
    expect(payment).toMatchObject({
      status: "SUCCESS",
      total: 200000,
      sourceKind: "appointment",
      sourceId: "apt-pay-an",
      patientName: AN.fullName,
      items: [{ label: "Phí khám · Khám chỉnh hình tổng quát", amount: 200000 }],
    });

    const detail = await getPayableSource("appointment", "apt-pay-an");
    expect(detail.items[0]).toMatchObject({
      status: "PAID",
      receipt: { paymentId, receiptNo: payment.receiptNo },
    });
    expect((await listPayableSources()).items.map((item) => item.id)).not.toContain("apt-pay-an");
    expect(findMockAppointmentFee("apt-pay-an")?.examFeeStatus).toBe("PAID");
  });

  it("khoản đang nằm trong giao dịch chờ không tạo giao dịch thứ hai (409 MSG-49)", async () => {
    const request = {
      sourceKind: "appointment" as const,
      sourceId: "apt-pay-an",
      itemIds: [appointmentFeeItemId("apt-pay-an")],
    };
    await createOnlinePayment(request);
    await expect(createOnlinePayment(request)).rejects.toMatchObject({
      status: 409,
      code: "MSG-49",
    });
  });

  it("lịch đã hủy, lịch của hồ sơ không gắn, hoặc không có trong kho → 404 MSG-41", async () => {
    for (const id of ["apt-cancel-an", "apt-stranger", "apt-khong-co"]) {
      await expect(getPayableSource("appointment", id)).rejects.toMatchObject({
        status: 404,
        code: "MSG-41",
      });
      await expect(
        createOnlinePayment({
          sourceKind: "appointment",
          sourceId: id,
          itemIds: [appointmentFeeItemId(id)],
        }),
      ).rejects.toMatchObject({ status: 404, code: "MSG-41" });
    }
  });
});
