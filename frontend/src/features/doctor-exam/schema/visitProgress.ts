import { icdLabel } from "./icd10Catalog";
import { primaryDiagnosis } from "./examinationRules";
import type {
  Advice,
  Examination,
  ParaclinicalOrder,
  Prescription,
} from "../types/doctorExam";

// Visit progress and readiness checks before the treating Doctor closes the visit.

export type StepState = "TODO" | "IN_PROGRESS" | "DONE";
export type StepSummary = { state: StepState; summary: string };

export function examinationStep(
  examination: Examination | undefined,
): StepSummary {
  if (!examination?.status)
    return { state: "TODO", summary: "Chưa ghi nhận khám." };
  const primary = primaryDiagnosis(examination.diagnoses);
  const diagnosis = primary
    ? `Chẩn đoán chính: ${icdLabel(primary.code)}`
    : "Chưa có chẩn đoán chính.";
  return examination.status === "COMPLETED"
    ? { state: "DONE", summary: diagnosis }
    : { state: "IN_PROGRESS", summary: `Bản nháp. ${diagnosis}` };
}

/** Chỉ định đã hủy không tính vào điều kiện hoàn tất (BR-33). */
export function activeOrders(
  orders: readonly ParaclinicalOrder[],
): ParaclinicalOrder[] {
  return orders.filter((order) => order.status !== "CANCELLED");
}

/** Chỉ định đã có kết quả; CĐHA còn cần bác sĩ hoàn tất kết luận (UC42). */
export function orderIsResolved(order: ParaclinicalOrder): boolean {
  if (order.status !== "RESULTS_AVAILABLE") return false;
  return order.kind === "IMAGING" ? !!order.imaging?.findingsCompleted : true;
}

export function ordersStep(orders: readonly ParaclinicalOrder[]): StepSummary {
  const active = activeOrders(orders);
  if (active.length === 0)
    return { state: "TODO", summary: "Chưa có chỉ định cận lâm sàng." };
  const done = active.filter(orderIsResolved).length;
  return {
    state: done === active.length ? "DONE" : "IN_PROGRESS",
    summary: `${active.length} chỉ định, ${done} đã có kết quả.`,
  };
}

export function treatmentStep(
  advice: Advice | null | undefined,
  prescription: Prescription | null | undefined,
): StepSummary {
  const parts = [
    advice ? "Đã ghi lời dặn" : null,
    prescription?.status === "ISSUED"
      ? "Đã kê đơn"
      : prescription
        ? "Đơn thuốc nháp"
        : null,
  ].filter(Boolean);
  if (parts.length === 0)
    return { state: "TODO", summary: "Chưa ghi hướng điều trị." };
  return {
    state: advice && prescription?.status === "ISSUED" ? "DONE" : "IN_PROGRESS",
    summary: `${parts.join(", ")}.`,
  };
}

export type ChecklistItem = {
  id: string;
  label: string;
  ok: boolean;
  detail: string;
};

/** Điều kiện sẵn sàng phần khám: chẩn đoán chính + mọi chỉ định có kết quả hoặc đã hủy (BR-33). */
/** `unavailable`: nguồn dữ liệu của mục đó tải lỗi; chưa kiểm được thì coi là chưa đạt (BR-33). */
export function buildClosingChecklist(
  examination: Examination | undefined,
  orders: readonly ParaclinicalOrder[],
  unavailable: { examination?: boolean; orders?: boolean } = {},
): ChecklistItem[] {
  const primary = examination && primaryDiagnosis(examination.diagnoses);
  const waiting = activeOrders(orders).filter((o) => !orderIsResolved(o));
  const waitingNames = waiting
    .map((order) => {
      const releaseState = order.imaging?.findingsReleaseState;
      const releaseNote =
        releaseState === "RELEASE_PENDING"
          ? " (đang chờ phát hành)"
          : releaseState === "RELEASE_FAILED"
            ? " (phát hành chưa thành công)"
            : "";
      return `${order.serviceName}${releaseNote}`;
    })
    .join(", ");
  return [
    {
      id: "diagnosis",
      label: "Đã có chẩn đoán chính",
      ok:
        !unavailable.examination &&
        !!primary &&
        examination?.status === "COMPLETED",
      detail: unavailable.examination
        ? "Chưa đọc được ghi nhận khám. Tải lại rồi thử lại."
        : primary
          ? examination?.status === "COMPLETED"
            ? icdLabel(primary.code)
            : "Cần hoàn thành ghi nhận khám."
          : "Chưa có chẩn đoán chính.",
    },
    {
      id: "orders",
      label: "Mọi chỉ định đã có kết quả hoặc đã hủy",
      ok: !unavailable.orders && waiting.length === 0,
      detail: unavailable.orders
        ? "Chưa kiểm tra được trạng thái chỉ định. Tải lại rồi thử lại."
        : waiting.length === 0
          ? "Không còn chỉ định đang chờ."
          : `${waiting.length} chỉ định chưa xong: ${waitingNames}.`,
    },
  ];
}

export function isClinicalChecklistReady(
  items: readonly ChecklistItem[],
): boolean {
  return items.every((item) => item.ok);
}
