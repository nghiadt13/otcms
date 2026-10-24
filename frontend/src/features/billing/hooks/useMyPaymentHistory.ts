"use client";

import { listMyPayments } from "../api/patientBillingApi";
import type { PatientPayment } from "../types/patientBilling";
import { usePagedList } from "@/hooks/usePagedList";

/** Bộ lọc lịch sử: ngày yyyy-MM-dd (rỗng = không lọc), hồ sơ người khám (rỗng = tất cả). */
export interface PaymentHistoryFilter {
  from: string;
  to: string;
  patientId: string;
}

/**
 * Lịch sử thanh toán đã hoàn tất (chỉ đọc), phân trang theo `usePagedList`. Đổi bộ lọc thì về
 * trang đầu và hủy request cũ. Khoảng ngày sai do màn chặn trước (MSG-54), hook không gọi API.
 */
export function useMyPaymentHistory(filters: PaymentHistoryFilter, pageSize?: number) {
  return usePagedList<PatientPayment, PaymentHistoryFilter>(listMyPayments, filters, pageSize);
}
