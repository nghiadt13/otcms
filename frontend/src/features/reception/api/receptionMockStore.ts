import type { CheckInQueueEntry, ExamFeePayment } from "../types/checkIn";
import { QUEUE_COUNTER_SEED } from "./checkInMockData";
import { SEED_APPOINTMENTS, seedQueueEntries } from "./checkInMockAppointments";

// KHO GIẢ DÙNG CHUNG của feature reception, chỉ lớp API giả đọc ghi. Gồm: bản ghi hàng đợi do
// tiếp nhận tạo (phase S05 đọc bằng listCheckInQueueEntries()), số thứ tự theo phòng, khoản
// phí đã thu theo lịch hẹn và các khoản thu VNPay đang chờ. Khi nối BE thật, file này bị bỏ.

/** Khoản thu phí khám trong kho giả, kèm dữ liệu nội bộ để giả lập VNPay. */
export interface StoredExamFeePayment extends ExamFeePayment {
  patientId: string;
  appointmentId: string | null;
  serviceId: string | null;
  /** Số lần đã hỏi trạng thái khi đang chờ VNPay. */
  polls: number;
  /** Kết cục giả lập khi tạo QR: SUCCESS sau vài lần hỏi, FAILED ở lần hỏi đầu. */
  outcome: "SUCCESS" | "FAILED";
}

/** Dữ liệu để tạo bản ghi hàng đợi (số thứ tự và trạng thái do kho gán). */
export type CheckInQueueDraft = Omit<CheckInQueueEntry, "id" | "queueNumber" | "status">;

type Sequence = "visit" | "queue" | "payment" | "receipt";

let sequences: Record<Sequence, number>;
let queueEntries: CheckInQueueEntry[];
let queueCounters: Record<string, number>;
let paidAppointmentIds: Set<string>;
let payments: Map<string, StoredExamFeePayment>;
let forcedVnpayFailures: number;

/** Đưa kho về trạng thái ban đầu (dùng trong test và khi làm mới dữ liệu giả). */
export function resetReceptionMockStore(): void {
  sequences = { visit: 7000, queue: 3009, payment: 5000, receipt: 41 };
  queueEntries = seedQueueEntries();
  queueCounters = { ...QUEUE_COUNTER_SEED };
  paidAppointmentIds = new Set(
    SEED_APPOINTMENTS.filter((item) => item.fee.status === "PAID").map((item) => item.id),
  );
  payments = new Map();
  forcedVnpayFailures = 0;
}

resetReceptionMockStore();

/** Số tiếp theo của một dãy mã giả (visit, queue, payment, receipt). */
export function nextMockSequence(kind: Sequence): number {
  sequences[kind] += 1;
  return sequences[kind];
}

/** Mọi bản ghi hàng đợi do tiếp nhận tạo hoặc có sẵn hôm nay (bản sao, theo thứ tự tạo). */
export function listCheckInQueueEntries(): CheckInQueueEntry[] {
  return queueEntries.map((entry) => ({ ...entry }));
}

export function findQueueEntryByAppointment(appointmentId: string): CheckInQueueEntry | undefined {
  const found = queueEntries.find((entry) => entry.appointmentId === appointmentId);
  return found ? { ...found } : undefined;
}

export function findQueueEntryByVisit(visitId: string): CheckInQueueEntry | undefined {
  const found = queueEntries.find((entry) => entry.visitId === visitId);
  return found ? { ...found } : undefined;
}

/** Số người đang Đang chờ trong một phòng (không tính đang phục vụ, vắng, đã xong). */
export function countWaitingInRoom(roomName: string): number {
  return queueEntries.filter(
    (entry) => entry.roomName === roomName && entry.status === "WAITING",
  ).length;
}

/** Thêm bản ghi hàng đợi WAITING; số thứ tự = số đã cấp của phòng + 1. */
export function addCheckInQueueEntry(draft: CheckInQueueDraft): CheckInQueueEntry {
  const queueNumber = (queueCounters[draft.roomName] ?? 0) + 1;
  queueCounters[draft.roomName] = queueNumber;
  const entry: CheckInQueueEntry = {
    ...draft,
    id: `q-${nextMockSequence("queue")}`,
    queueNumber,
    status: "WAITING",
  };
  queueEntries.push(entry);
  return { ...entry };
}

export function isExamFeePaid(appointmentId: string): boolean {
  return paidAppointmentIds.has(appointmentId);
}

export function markExamFeePaid(appointmentId: string): void {
  paidAppointmentIds.add(appointmentId);
}

export function findPayment(paymentId: string): StoredExamFeePayment | undefined {
  const found = payments.get(paymentId);
  return found ? { ...found, vnpay: found.vnpay ? { ...found.vnpay } : null } : undefined;
}

export function savePayment(payment: StoredExamFeePayment): void {
  payments.set(payment.id, { ...payment });
}

export function updatePayment(
  paymentId: string,
  patch: Partial<Pick<StoredExamFeePayment, "status" | "receiptNo" | "polls">>,
): StoredExamFeePayment {
  const current = payments.get(paymentId);
  if (!current) throw new Error(`Không có khoản thu ${paymentId} trong kho giả.`);
  const next: StoredExamFeePayment = { ...current, ...patch };
  payments.set(paymentId, next);
  return { ...next };
}

/** Test: khoản VNPay kế tiếp được tạo sẽ thất bại (transactionRef có "FAIL"). */
export function forceNextVnpayFailure(): void {
  forcedVnpayFailures += 1;
}

/** Kết cục của khoản VNPay vừa tạo: dùng một lần cho lần tạo đang xử lý. */
export function takeVnpayOutcome(): "SUCCESS" | "FAILED" {
  if (forcedVnpayFailures > 0) {
    forcedVnpayFailures -= 1;
    return "FAILED";
  }
  return "SUCCESS";
}
