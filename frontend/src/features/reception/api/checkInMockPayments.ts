import { mockFail } from "@/lib/api/mockServer";
import type { ExamFeePayment, ExamFeePaymentRequest } from "../types/checkIn";
import { RACE_APPOINTMENT_ID } from "./checkInMockAppointments";
import { MOCK_TODAY, WALK_IN_SERVICES } from "./checkInMockData";
import {
  appointmentOrFail,
  feeOf,
  patientOrFail,
  statusOf,
  toVietnamIso,
} from "./checkInMockState";
import {
  findPayment,
  isExamFeePaid,
  markExamFeePaid,
  nextMockSequence,
  savePayment,
  takeVnpayOutcome,
  updatePayment,
  type StoredExamFeePayment,
} from "./receptionMockStore";

// Thu phí khám trong lớp API giả: tiền mặt thành công ngay; VNPay trả PENDING kèm QR rồi kết cục
// đến theo số lần hỏi hoặc theo hạn 15 phút (BR-51).

const PAYMENT_WINDOW_MS = 15 * 60_000;
const SUCCESS_AFTER_POLLS = 3;
const VNPAY_QR_IMAGE_URL = "/mock/vnpay-qr.svg";

const compactToday = () => MOCK_TODAY.replace(/-/g, "");

function nextReceiptNo(prefix: string): string {
  return `${prefix}-${compactToday()}-${String(nextMockSequence("receipt")).padStart(4, "0")}`;
}

export function toPublicPayment(payment: StoredExamFeePayment): ExamFeePayment {
  return {
    id: payment.id,
    method: payment.method,
    amount: payment.amount,
    status: payment.status,
    receiptNo: payment.receiptNo,
    vnpay: payment.vnpay ? { ...payment.vnpay } : null,
  };
}

/** POST exam-fee-payments. Tiền mặt thành công ngay; VNPay trả PENDING kèm QR. */
export function collectExamFeeMock(body: ExamFeePaymentRequest): ExamFeePayment {
  const patient = patientOrFail(body.patientId);
  if (body.appointmentId !== null && body.serviceId !== null) mockFail(400, "MSG-39");

  let amount: number;
  if (body.appointmentId !== null) {
    const seed = appointmentOrFail(body.appointmentId);
    if (seed.patientId !== patient.id) mockFail(403, "MSG-40");
    if (seed.id === RACE_APPOINTMENT_ID && !isExamFeePaid(seed.id)) {
      // Giả lập: bệnh nhân vừa trả online trong lúc lễ tân thu tại quầy.
      markExamFeePaid(seed.id);
      mockFail(409, "MSG-49");
    }
    if (statusOf(seed) !== "BOOKED" || feeOf(seed).status !== "PENDING_PAYMENT") {
      mockFail(409, "MSG-49");
    }
    amount = seed.fee.amount;
  } else if (body.serviceId !== null) {
    const service = WALK_IN_SERVICES.find((item) => item.id === body.serviceId);
    if (!service) mockFail(400, "MSG-39");
    amount = service.price;
  } else {
    mockFail(400, "MSG-39");
  }

  const base = {
    id: `pay-${nextMockSequence("payment")}`,
    amount,
    patientId: patient.id,
    appointmentId: body.appointmentId,
    serviceId: body.serviceId,
    polls: 0,
  };

  if (body.method === "CASH") {
    const payment: StoredExamFeePayment = {
      ...base,
      method: "CASH",
      status: "SUCCESS",
      receiptNo: nextReceiptNo("PT"),
      vnpay: null,
      outcome: "SUCCESS",
    };
    savePayment(payment);
    if (body.appointmentId !== null) markExamFeePaid(body.appointmentId);
    return toPublicPayment(payment);
  }

  const outcome = takeVnpayOutcome();
  const reference = `OTC-${compactToday()}-${String(nextMockSequence("receipt")).padStart(4, "0")}${
    outcome === "FAILED" ? "FAIL" : ""
  }`;
  const payment: StoredExamFeePayment = {
    ...base,
    method: "VNPAY",
    status: "PENDING",
    receiptNo: null,
    vnpay: {
      qrImageUrl: VNPAY_QR_IMAGE_URL,
      transactionRef: reference,
      expiresAt: toVietnamIso(new Date(Date.now() + PAYMENT_WINDOW_MS)),
    },
    outcome,
  };
  savePayment(payment);
  return toPublicPayment(payment);
}

/** GET exam-fee-payments/{id}: mỗi lần hỏi đếm một lần; đủ số lần thì SUCCESS, hết hạn thì FAILED. */
export function getExamFeePaymentMock(paymentId: string): ExamFeePayment {
  const payment = findPayment(paymentId) ?? mockFail(404, "MSG-41");
  if (payment.status !== "PENDING") return toPublicPayment(payment);

  const expired = payment.vnpay !== null && Date.parse(payment.vnpay.expiresAt) <= Date.now();
  if (expired || payment.outcome === "FAILED") {
    return toPublicPayment(updatePayment(paymentId, { status: "FAILED" }));
  }
  const polls = payment.polls + 1;
  if (polls < SUCCESS_AFTER_POLLS) return toPublicPayment(updatePayment(paymentId, { polls }));

  const settled = updatePayment(paymentId, {
    polls,
    status: "SUCCESS",
    receiptNo: nextReceiptNo("PT"),
  });
  if (settled.appointmentId !== null) markExamFeePaid(settled.appointmentId);
  return toPublicPayment(settled);
}

/** Khoản thu thành công của đúng bệnh nhân và đúng lịch hẹn (hoặc dịch vụ vãng lai). */
export function paymentCovers(
  paymentId: string | null,
  where: { patientId: string; appointmentId?: string; serviceId?: string },
): boolean {
  if (paymentId === null) return false;
  const payment = findPayment(paymentId);
  if (!payment || payment.status !== "SUCCESS" || payment.patientId !== where.patientId) return false;
  return where.appointmentId !== undefined
    ? payment.appointmentId === where.appointmentId
    : payment.serviceId === where.serviceId;
}
