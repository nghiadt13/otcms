import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resetReceptionMockStore } from "./receptionMockStore";
import { rejectionOf } from "./checkInTestErrors";
import { collectExamFee, getCheckInContext, getExamFeePayment } from "./checkInApi";
import { forceNextVnpayFailure } from "./receptionMockStore";

beforeEach(() => resetReceptionMockStore());
afterEach(() => vi.useRealTimers());

describe("collectExamFee", () => {
  it("tiền mặt thành công: phiếu thu PT-, và lần tải bối cảnh sau thấy đã trả", async () => {
    const paid = await collectExamFee({
      patientId: "pat-an",
      appointmentId: "apt-1001",
      serviceId: null,
      method: "CASH",
    });
    expect(paid).toMatchObject({ method: "CASH", amount: 200000, status: "SUCCESS", vnpay: null });
    expect(paid.receiptNo).toMatch(/^PT-\d{8}-\d{4}$/);

    const context = await getCheckInContext({ appointmentId: "apt-1001" });
    expect(context.options[0]).toMatchObject({ examFee: { status: "PAID" } });
  });

  it("khoản đã thu rồi thì thu lại bị 409 MSG-49", async () => {
    const request = { patientId: "pat-an", appointmentId: "apt-1001", serviceId: null, method: "CASH" as const };
    await collectExamFee(request);
    expect(await rejectionOf(collectExamFee(request))).toMatchObject({ status: 409, code: "MSG-49" });
  });

  it("lịch vừa được trả online trong lúc thu (apt-race): 409 MSG-49, lần sau thấy đã trả", async () => {
    const request = { patientId: "pat-p05", appointmentId: "apt-race", serviceId: null, method: "CASH" as const };
    expect(await rejectionOf(collectExamFee(request))).toMatchObject({ status: 409, code: "MSG-49" });
    const context = await getCheckInContext({ appointmentId: "apt-race" });
    expect(context.options[0]).toMatchObject({ examFee: { status: "PAID" } });
  });

  it("VNPay tạo khoản PENDING có QR; ba lần hỏi thì SUCCESS và phí thành đã trả", async () => {
    const pending = await collectExamFee({
      patientId: "pat-an",
      appointmentId: "apt-1001",
      serviceId: null,
      method: "VNPAY",
    });
    expect(pending.status).toBe("PENDING");
    expect(pending.vnpay?.qrImageUrl).toBe("/mock/vnpay-qr.svg");
    expect(pending.vnpay?.transactionRef).toMatch(/^OTC-\d{8}-\d{4}$/);
    expect(pending.vnpay?.expiresAt).toMatch(/\+07:00$/);

    const first = await getExamFeePayment(pending.id);
    const second = await getExamFeePayment(pending.id);
    expect([first.status, second.status]).toEqual(["PENDING", "PENDING"]);
    const settled = await getExamFeePayment(pending.id);
    expect(settled.status).toBe("SUCCESS");
    expect(settled.receiptNo).toMatch(/^PT-/);
    expect((await getCheckInContext({ appointmentId: "apt-1001" })).options[0]).toMatchObject({
      examFee: { status: "PAID" },
    });
  });

  it("VNPay bị từ chối: FAILED ở lần hỏi đầu, mã giao dịch mang dấu FAIL", async () => {
    forceNextVnpayFailure();
    const pending = await collectExamFee({
      patientId: "pat-an",
      appointmentId: "apt-1001",
      serviceId: null,
      method: "VNPAY",
    });
    expect(pending.vnpay?.transactionRef).toContain("FAIL");
    expect((await getExamFeePayment(pending.id)).status).toBe("FAILED");
  });

  it("VNPay quá 15 phút không có kết quả: FAILED", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    const pending = await collectExamFee({
      patientId: "pat-an",
      appointmentId: "apt-1001",
      serviceId: null,
      method: "VNPAY",
    });
    vi.setSystemTime(Date.now() + 16 * 60_000);
    expect((await getExamFeePayment(pending.id)).status).toBe("FAILED");
  });

  it("vãng lai thu theo dịch vụ đã chọn; không có lịch và không có dịch vụ thì 400 MSG-39", async () => {
    const paid = await collectExamFee({
      patientId: "pat-moi",
      appointmentId: null,
      serviceId: "svc-kham-cs",
      method: "CASH",
    });
    expect(paid.amount).toBe(250000);
    expect(
      await rejectionOf(
        collectExamFee({ patientId: "pat-moi", appointmentId: null, serviceId: null, method: "CASH" }),
      ),
    ).toMatchObject({ status: 400, code: "MSG-39" });
  });

  it("phương thức lạ bị từ chối với MSG-39 trước khi gọi kho", async () => {
    expect(
      await rejectionOf(
        collectExamFee({ patientId: "pat-an", appointmentId: "apt-1001", serviceId: null, method: "CARD" as never }),
      ),
    ).toMatchObject({ status: 400, code: "MSG-39" });
  });

  it("mã khoản thu không có thì 404 MSG-41", async () => {
    expect(await rejectionOf(getExamFeePayment("pay-khong-co"))).toMatchObject({ status: 404, code: "MSG-41" });
  });
});

