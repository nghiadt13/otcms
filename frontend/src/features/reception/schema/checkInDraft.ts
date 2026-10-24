import type { PaymentMethod } from "@/lib/domain/clinicVisit";
import type {
  CheckInOption,
  CheckInRequest,
  CheckInTarget,
  ExamFeePayment,
  ExamFeePaymentRequest,
} from "../types/checkIn";

// Các phép suy ra thuần cho màn tiếp nhận: khóa lựa chọn, phí đang áp dụng, và dựng body gửi BE.
// Không gọi API, không giữ state. CheckInPanel dùng để nối state với request.

/** Khóa ổn định của một lựa chọn (value của RadioGroup). */
export function optionKey(option: CheckInOption): string {
  if (option.kind === "APPOINTMENT") return `APPOINTMENT:${option.appointmentId}`;
  if (option.kind === "RETURN_VISIT") return `RETURN_VISIT:${option.visitId}`;
  return "WALK_IN";
}

export function findOption(options: readonly CheckInOption[], key: string | null): CheckInOption | null {
  if (key === null) return null;
  return options.find((option) => optionKey(option) === key) ?? null;
}

/** Lựa chọn mặc định: đúng lịch hẹn đã mở từ danh sách, không thì lựa chọn đầu tiên. */
export function defaultOptionKey(
  options: readonly CheckInOption[],
  target: CheckInTarget,
): string | null {
  const fromTarget =
    target.kind === "appointment"
      ? options.find((option) => option.kind === "APPOINTMENT" && option.appointmentId === target.id)
      : undefined;
  const chosen = fromTarget ?? options[0];
  return chosen ? optionKey(chosen) : null;
}

/** Phí đang áp dụng cho lựa chọn; vãng lai chưa chọn dịch vụ thì chưa có phí để hiện. */
export type ExamFeeView =
  | { kind: "NEEDS_SERVICE" }
  | { kind: "FEE"; status: "PAID" | "PENDING_PAYMENT" | "NOT_REQUIRED"; amount: number; paidOnline: boolean };

/**
 * Khoản đã thu thành công trong màn (payment SUCCESS) làm phí thành PAID. Panel đặt lại payment
 * mỗi khi đổi lựa chọn hoặc dịch vụ (khi chưa khóa), nên payment luôn thuộc lựa chọn hiện tại.
 */
export function examFeeView(
  option: CheckInOption | null,
  serviceId: string | null,
  payment: ExamFeePayment | null,
): ExamFeeView | null {
  if (!option) return null;
  const collected = payment?.status === "SUCCESS";
  if (option.kind === "APPOINTMENT") {
    return {
      kind: "FEE",
      status: collected ? "PAID" : option.examFee.status,
      amount: option.examFee.amount,
      paidOnline: option.examFee.paidOnline,
    };
  }
  if (option.kind === "RETURN_VISIT") {
    return { kind: "FEE", status: "NOT_REQUIRED", amount: 0, paidOnline: false };
  }
  const service = option.services.find((item) => item.id === serviceId);
  if (!service) return { kind: "NEEDS_SERVICE" };
  return {
    kind: "FEE",
    status: collected ? "PAID" : "PENDING_PAYMENT",
    amount: service.price,
    paidOnline: false,
  };
}

/** Body thu phí: lịch hẹn gửi appointmentId, vãng lai gửi serviceId. */
export function buildExamFeeRequest(
  patientId: string,
  option: CheckInOption,
  serviceId: string | null,
  method: PaymentMethod,
): ExamFeePaymentRequest {
  return {
    patientId,
    appointmentId: option.kind === "APPOINTMENT" ? option.appointmentId : null,
    serviceId: option.kind === "WALK_IN" ? serviceId : null,
    method,
  };
}

/** Body tiếp nhận: đúng một trong appointmentId, visitId, serviceId theo lựa chọn. */
export function buildCheckInRequest(input: {
  patientId: string;
  option: CheckInOption;
  serviceId: string | null;
  priorityGroupCode: string | null;
  payment: ExamFeePayment | null;
}): CheckInRequest {
  const { option } = input;
  return {
    patientId: input.patientId,
    appointmentId: option.kind === "APPOINTMENT" ? option.appointmentId : null,
    visitId: option.kind === "RETURN_VISIT" ? option.visitId : null,
    serviceId: option.kind === "WALK_IN" ? input.serviceId : null,
    priorityGroupCode: input.priorityGroupCode,
    examFeePaymentId: input.payment?.status === "SUCCESS" ? input.payment.id : null,
  };
}
