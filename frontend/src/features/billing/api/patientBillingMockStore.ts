import { markMockAppointmentFeePaid } from "@/lib/mock/sharedAppointmentFeeMock";
import { getLinkedProfileRecords } from "@/lib/patient-scope/linkedProfilesMockStore";
import type { PaymentSourceKind } from "../types/patientBilling";
import {
  appointmentSourceOf,
  appointmentSourcesOf,
} from "./patientBillingAppointmentSources";
import { PENDING_TWICE_ITEM_ID } from "./patientBillingMockData";
import {
  createInitialMockState,
  type MockState,
  type PaymentOutcome,
  type StoredPayment,
  type StoredSource,
} from "./patientBillingMockState";

export type { StoredItem, StoredPayment, StoredSource } from "./patientBillingMockState";

// KHO GIẢ trong bộ nhớ của thanh toán khu bệnh nhân. Chỉ lớp API giả dùng. Kết quả giao dịch
// (PENDING → SUCCESS/FAILED), khóa khoản đang thanh toán và số thứ tự nằm ở đây; khi nối BE
// thật, phần này bị bỏ, không có luật nào của FE được giữ lại. Nguồn lịch hẹn không lưu ở đây mà
// đọc từ kho phí khám dùng chung (`lib/mock/sharedAppointmentFeeMock.ts`).

/** Tổng chia hết cho 1.000.000 đồng thì giao dịch giả lập thất bại (kịch bản phase 06 §8). */
const FAILING_TOTAL_STEP = 1_000_000;
/** Múi giờ Việt Nam không có giờ mùa hè: cộng cố định 7 giờ rồi ghi offset +07:00. */
const VN_OFFSET_MS = 7 * 60 * 60 * 1000;

let state: MockState = createInitialMockState();

/** Đặt lại kho giả (test và demo). `failSources`/`failPayments` làm lần tải kế tiếp lỗi 500 MSG-04. */
export function resetPatientBillingMock(
  options: { failSources?: boolean; failPayments?: boolean } = {},
): void {
  state = createInitialMockState();
  state.failSources = options.failSources ?? false;
  state.failPayments = options.failPayments ?? false;
}

export function isSourceListFailing(): boolean {
  return state.failSources;
}

export function isPaymentListFailing(): boolean {
  return state.failPayments;
}

/**
 * Mọi nguồn: kho riêng của thanh toán (lượt khám và lịch hẹn giữ riêng như `apt-lan-27`) trước, rồi
 * lịch hẹn từ kho phí dùng chung (bỏ mã đã có trong kho riêng). Kho riêng là kho sống: nơi gọi phải
 * sao chép trước khi trả ra ngoài.
 */
export function listStoredSources(): StoredSource[] {
  const own = state.sources;
  const shared = appointmentSourcesOf(getLinkedProfileRecords(), state.payments).filter(
    (source) => !own.some((item) => item.kind === source.kind && item.id === source.id),
  );
  return [...own, ...shared];
}

export function findStoredSource(kind: PaymentSourceKind, id: string): StoredSource | undefined {
  return (
    state.sources.find((source) => source.kind === kind && source.id === id) ??
    (kind === "appointment"
      ? appointmentSourceOf(id, getLinkedProfileRecords(), state.payments)
      : undefined)
  );
}

export function findStoredPayment(id: string): StoredPayment | undefined {
  return state.payments.find((payment) => payment.id === id);
}

/** Mọi thanh toán đã ghi nhận (kể cả tiền mặt đã thu). Nơi gọi tự lọc trạng thái. */
export function listStoredPayments(): StoredPayment[] {
  return state.payments;
}

/** Khoản đang nằm trong một giao dịch PENDING: không cho tạo thêm giao dịch (chống trả trùng). */
export function lockedItemIds(): Set<string> {
  return new Set(
    state.payments
      .filter((payment) => payment.status === "PENDING")
      .flatMap((payment) => payment.itemIds),
  );
}

function decideOutcome(
  total: number,
  itemIds: string[],
): { outcome: PaymentOutcome; pendingChecksLeft: number } {
  if (total > 0 && total % FAILING_TOTAL_STEP === 0)
    return { outcome: "FAILED", pendingChecksLeft: 0 };
  if (itemIds.includes(PENDING_TWICE_ITEM_ID))
    return { outcome: "PENDING_THEN_SUCCESS", pendingChecksLeft: 2 };
  return { outcome: "SUCCESS", pendingChecksLeft: 0 };
}

/** Tạo giao dịch PENDING cho các khoản đã chọn; tổng tính từ giá trong kho, không từ FE. */
export function createStoredPayment(source: StoredSource, itemIds: string[]): StoredPayment {
  state.paymentSeq += 1;
  const items = source.items.filter((item) => itemIds.includes(item.id));
  const total = items.reduce((sum, item) => sum + item.amount, 0);
  const payment: StoredPayment = {
    id: `pay-mock-${state.paymentSeq}`,
    sourceKind: source.kind,
    sourceId: source.id,
    patient: { ...source.patient },
    itemIds: [...itemIds],
    total,
    method: "VNPAY",
    status: "PENDING",
    paidAt: null,
    vnpayRef: null,
    receiptNo: null,
    items: items.map((item) => ({ label: item.label, amount: item.amount })),
    queueAssignments: [],
    ...decideOutcome(total, itemIds),
  };
  state.payments.push(payment);
  return payment;
}

/** Mỗi lần kiểm tra trạng thái đẩy giao dịch một bước, giống BE nhận kết quả từ cổng. */
export function advanceStoredPayment(payment: StoredPayment): void {
  if (payment.status !== "PENDING") return;
  if (payment.outcome === "PENDING_THEN_SUCCESS" && payment.pendingChecksLeft > 0) {
    payment.pendingChecksLeft -= 1;
    return;
  }
  if (payment.outcome === "FAILED") {
    payment.status = "FAILED";
    return;
  }
  settleSuccess(payment);
}

function settleSuccess(payment: StoredPayment): void {
  const source = state.sources.find(
    (candidate) => candidate.kind === payment.sourceKind && candidate.id === payment.sourceId,
  );
  state.receiptSeq += 1;
  payment.status = "SUCCESS";
  payment.paidAt = vietnamIsoNow();
  payment.vnpayRef = `VNP-${payment.id}`;
  payment.receiptNo = `PT-${state.receiptSeq}`;
  // Phí khám lịch hẹn nằm ở kho phí dùng chung: đánh dấu đã trả để khu đặt lịch thấy PAID.
  if (payment.sourceKind === "appointment") markMockAppointmentFeePaid(payment.sourceId);
  if (!source) return;
  const paidItems = source.items.filter((item) => payment.itemIds.includes(item.id));
  for (const item of paidItems) {
    item.status = "PAID";
    item.receipt = { paymentId: payment.id, receiptNo: payment.receiptNo };
  }
  payment.queueAssignments = paidItems.flatMap((item) =>
    item.room ? [{ roomName: item.room, queueNumber: nextQueueNumber(item.room) }] : [],
  );
}

function nextQueueNumber(room: string): number {
  const next = state.queueNext[room] ?? 1;
  state.queueNext[room] = next + 1;
  return next;
}

function vietnamIsoNow(): string {
  return `${new Date(Date.now() + VN_OFFSET_MS).toISOString().slice(0, 19)}+07:00`;
}
