import type { PaymentMethod, PaymentStatus } from "@/lib/domain/clinicVisit";
import type {
  PaymentItemStatus,
  PaymentSourceKind,
} from "../types/patientBilling";
import {
  SEED_HISTORY,
  SEED_PATIENTS,
  SEED_SOURCES,
  type SeedHistoryRow,
  type SeedPatient,
  type SeedSource,
} from "./patientBillingMockData";

// Hình dạng bản ghi trong kho giả thanh toán và dữ liệu khởi tạo từ seed. Tách khỏi kho để file
// logic giao dịch (patientBillingMockStore.ts) gọn; không có luật nghiệp vụ ở đây.

export interface StoredItem {
  id: string;
  label: string;
  amount: number;
  status: PaymentItemStatus;
  cancellationReason: string | null;
  receipt: { paymentId: string; receiptNo: string } | null;
  room: string | null;
}

export interface StoredSource {
  kind: PaymentSourceKind;
  id: string;
  date: string;
  patient: SeedPatient;
  items: StoredItem[];
}

/** Kết cục giả lập của một giao dịch: quyết định khi tạo, lộ ra ở lần kiểm tra. */
export type PaymentOutcome = "SUCCESS" | "FAILED" | "PENDING_THEN_SUCCESS";

export interface StoredPayment {
  id: string;
  sourceKind: PaymentSourceKind;
  sourceId: string;
  patient: SeedPatient;
  itemIds: string[];
  total: number;
  method: PaymentMethod;
  status: PaymentStatus;
  paidAt: string | null;
  vnpayRef: string | null;
  receiptNo: string | null;
  items: { label: string; amount: number }[];
  queueAssignments: { roomName: string; queueNumber: number }[];
  outcome: PaymentOutcome;
  pendingChecksLeft: number;
}

export interface MockState {
  sources: StoredSource[];
  payments: StoredPayment[];
  paymentSeq: number;
  receiptSeq: number;
  /** Số thứ tự kế tiếp theo phòng. */
  queueNext: Record<string, number>;
  failSources: boolean;
  failPayments: boolean;
}

/** Số thứ tự đã cấp trước đó của các phòng trong dữ liệu giả. */
const QUEUE_START: Record<string, number> = {
  "Phòng MRI 1": 4,
  "Phòng xét nghiệm 2": 11,
  "Phòng nội soi 1": 2,
};

function toStoredSource(seed: SeedSource): StoredSource {
  return {
    kind: seed.kind,
    id: seed.id,
    date: seed.date,
    patient: { ...SEED_PATIENTS[seed.patient] },
    items: seed.items.map((item) => ({
      id: item.id,
      label: item.label,
      amount: item.amount,
      status: item.status,
      cancellationReason: item.cancellationReason ?? null,
      receipt: item.receipt ? { ...item.receipt } : null,
      room: item.room ?? null,
    })),
  };
}

/** Thanh toán đã hoàn tất trong seed: không có nguồn trực tiếp, không có số thứ tự. */
function toHistoryPayment([
  id,
  paidAt,
  patientKey,
  method,
  lines,
]: SeedHistoryRow): StoredPayment {
  return {
    id,
    sourceKind: "visit",
    sourceId: "",
    patient: { ...SEED_PATIENTS[patientKey] },
    itemIds: [],
    total: lines.reduce((sum, [, amount]) => sum + amount, 0),
    method,
    status: "SUCCESS",
    paidAt,
    vnpayRef:
      method === "VNPAY"
        ? `VNP-${id}-${paidAt.slice(0, 10).replace(/-/g, "")}`
        : null,
    receiptNo: `PT-${id.slice(3)}`,
    items: lines.map(([label, amount]) => ({ label, amount })),
    queueAssignments: [],
    outcome: "SUCCESS",
    pendingChecksLeft: 0,
  };
}

/** Kho ban đầu từ seed. Mỗi lần reset dựng lại từ đầu, không dùng chung đối tượng với seed. */
export function createInitialMockState(): MockState {
  return {
    sources: SEED_SOURCES.map(toStoredSource),
    payments: SEED_HISTORY.map(toHistoryPayment),
    paymentSeq: 0,
    // Phiếu thu mới bắt đầu sau dãy PT-1001..PT-1029 của dữ liệu giả.
    receiptSeq: 1029,
    queueNext: { ...QUEUE_START },
    failSources: false,
    failPayments: false,
  };
}
