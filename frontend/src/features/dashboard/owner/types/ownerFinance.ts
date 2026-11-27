import type { DashboardPeriod } from "@/features/dashboard/types/dashboardOverview";

/**
 * Hợp đồng dữ liệu trang Tài chính của Owner, khớp `GET /api/v1/owner/finance`.
 * Tiền là số nguyên VND; thời điểm là chuỗi ISO. `pending` là ảnh chụp lúc này (khoản còn nợ khách),
 * không gắn với kỳ.
 */
export type FinanceSummary = {
  collected: number;
  refunded: number;
  refundedCount: number;
  pending: number;
  pendingCount: number;
};

/** `name: null` là khoản hoàn không rõ người xác nhận. */
export type RefundByPerson = {
  name: string | null;
  role: string | null;
  count: number;
  amount: number;
};

export type RefundStatus = "REFUND_PENDING" | "REFUNDED";

export type RefundRow = {
  itemId: string;
  invoiceNumber: string;
  patientName: string | null;
  description: string;
  amount: number;
  status: RefundStatus;
  reason: string | null;
  paidAt: string | null;
  refundedAt: string | null;
  refundedByName: string | null;
  refundedByRole: string | null;
};

export type OwnerFinance = {
  period: DashboardPeriod;
  summary: FinanceSummary;
  byPerson: RefundByPerson[];
  refunds: RefundRow[];
};
