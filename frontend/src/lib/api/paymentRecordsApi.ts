import { apiRequest } from "@/lib/api/client";
import type { PageResponse } from "@/lib/api/pagination";
import { toQueryString } from "@/lib/api/pagination";
import type { InvoiceItemStatus } from "@/lib/api/billingApi";

// Lịch sử và biên lai giao dịch đã xác nhận (BE: PaymentController/BillingDetailController).
// Mã thanh toán (`id`) chính là mã lần thanh toán (payment attempt) đã CONFIRMED.

/** Một dòng trong danh sách `GET /api/v1/payments` (chưa có tên bệnh nhân). */
export type PaymentRecord = {
  id: string;
  invoiceId: string;
  patientId: string;
  invoiceNumber: string;
  amount: number;
  currency: string;
  /** COUNTER (tiền mặt), COUNTER_QR hoặc ONLINE (PayOS). */
  channel: string;
  confirmedAt: string;
};

/** Một khoản nằm trong biên lai. `refundReason` chỉ có với ADMIN/OWNER. */
export type PaymentDetailLine = {
  itemId: string;
  lineNumber: number;
  description: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  status: InvoiceItemStatus;
  refundReason: string | null;
};

/** Chi tiết giao dịch: `GET /api/v1/payments/{id}` và JSON của `/receipt`. */
export type PaymentDetail = {
  id: string;
  receiptNumber: string;
  invoiceId: string;
  invoiceNumber: string;
  patientProfileId: string;
  patientName: string;
  amount: number;
  currency: string;
  /** CASH (quầy) hoặc PAYOS. */
  method: "CASH" | "PAYOS";
  channel: string;
  confirmedAt: string;
  /** Lễ tân thu tiền (chỉ thu tại quầy). */
  receivedByName: string | null;
  /** Mã giao dịch PayOS (chỉ thanh toán online). */
  providerTransactionReference: string | null;
  items: PaymentDetailLine[];
};

export type PaymentListQuery = {
  patientId?: string;
  /** yyyy-mm-dd */
  from?: string;
  /** yyyy-mm-dd */
  to?: string;
  page?: number;
  size?: number;
};

export function listPayments(
  query: PaymentListQuery,
  signal?: AbortSignal,
): Promise<PageResponse<PaymentRecord>> {
  return apiRequest<PageResponse<PaymentRecord>>(
    `/api/v1/payments${toQueryString({
      patientId: query.patientId,
      from: query.from,
      to: query.to,
      page: query.page ?? 0,
      size: query.size ?? 20,
    })}`,
    { signal },
  );
}

export function getPaymentDetail(
  paymentId: string,
  signal?: AbortSignal,
): Promise<PaymentDetail> {
  return apiRequest<PaymentDetail>(
    `/api/v1/payments/${encodeURIComponent(paymentId)}`,
    { signal },
  );
}

/** Biên lai dạng JSON (lễ tân). BN xem chi tiết qua getPaymentDetail. */
export function getPaymentReceipt(
  paymentId: string,
  signal?: AbortSignal,
): Promise<PaymentDetail> {
  return apiRequest<PaymentDetail>(
    `/api/v1/payments/${encodeURIComponent(paymentId)}/receipt`,
    { signal },
  );
}

/** Đường dẫn PDF biên lai (lễ tân), tải bằng apiDownload để kèm phiên đăng nhập. */
export function receiptPdfPath(paymentId: string) {
  return `/api/v1/payments/${encodeURIComponent(paymentId)}/receipt.pdf` as const;
}
