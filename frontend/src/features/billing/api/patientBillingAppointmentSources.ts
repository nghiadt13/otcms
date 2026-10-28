import {
  findMockAppointmentFee,
  listMockAppointmentFees,
  type MockAppointmentFee,
} from "@/lib/mock/sharedAppointmentFeeMock";
import type { LinkedProfile } from "@/lib/patient-scope/types";
import type {
  StoredItem,
  StoredPayment,
  StoredSource,
} from "./patientBillingMockState";

// Nguồn "lịch hẹn" của thanh toán, dựng từ kho phí khám dùng chung
// (lib/mock/sharedAppointmentFeeMock.ts) mỗi lần đọc, không lưu bản sao, nên khu đặt lịch và khu
// thanh toán luôn thấy cùng một dữ liệu. Nguồn là lịch Đã đặt của hồ sơ đã gắn; khoản là phí khám
// (`fee-<mã lịch>`); biên lai lấy từ giao dịch thành công của khoản đó. Chỉ lớp API giả; xóa khi nối BE.

/** Mã khoản phí khám của một lịch hẹn. */
export const appointmentFeeItemId = (appointmentId: string): string =>
  `fee-${appointmentId}`;

/** Lịch còn hiệu lực có phí (đã trả hoặc chưa trả) của một hồ sơ đã gắn. */
function isBillableFee(
  fee: MockAppointmentFee,
  linkedIds: ReadonlySet<string>,
): boolean {
  return (
    fee.appointmentStatus === "BOOKED" &&
    fee.examFeeStatus !== "NOT_REQUIRED" &&
    linkedIds.has(fee.profileId)
  );
}

function toStoredSource(
  fee: MockAppointmentFee,
  payments: readonly StoredPayment[],
): StoredSource {
  const itemId = appointmentFeeItemId(fee.appointmentId);
  const paidBy = payments.find(
    (payment) =>
      payment.status === "SUCCESS" &&
      payment.sourceKind === "appointment" &&
      payment.sourceId === fee.appointmentId &&
      payment.itemIds.includes(itemId),
  );
  const receiptNo = paidBy?.receiptNo ?? null;
  const item: StoredItem = {
    id: itemId,
    label: `Phí khám · ${fee.serviceName}`,
    amount: fee.examFeeAmount,
    status: fee.examFeeStatus === "PAID" ? "PAID" : "PENDING_PAYMENT",
    cancellationReason: null,
    receipt: paidBy && receiptNo ? { paymentId: paidBy.id, receiptNo } : null,
    room: null,
  };
  return {
    kind: "appointment",
    id: fee.appointmentId,
    date: fee.startAt,
    patient: {
      id: fee.profileId,
      fullName: fee.patientName,
      relationship: fee.relationship,
    },
    items: [item],
  };
}

/** Mọi nguồn lịch hẹn của các hồ sơ đã gắn (gồm cả đã trả, để lọc chờ thanh toán ở lớp gọi). */
export function appointmentSourcesOf(
  linked: readonly LinkedProfile[],
  payments: readonly StoredPayment[],
): StoredSource[] {
  const linkedIds = new Set(linked.map((profile) => profile.id));
  return listMockAppointmentFees()
    .filter((fee) => isBillableFee(fee, linkedIds))
    .map((fee) => toStoredSource(fee, payments));
}

/**
 * Một nguồn lịch hẹn theo mã. Không có trong kho, đã hủy, chưa đặt, không có phí, hoặc không thuộc
 * hồ sơ đã gắn → `undefined` (lớp gọi trả 404 MSG-41).
 */
export function appointmentSourceOf(
  appointmentId: string,
  linked: readonly LinkedProfile[],
  payments: readonly StoredPayment[],
): StoredSource | undefined {
  const fee = findMockAppointmentFee(appointmentId);
  const linkedIds = new Set(linked.map((profile) => profile.id));
  if (!fee || !isBillableFee(fee, linkedIds)) return undefined;
  return toStoredSource(fee, payments);
}
