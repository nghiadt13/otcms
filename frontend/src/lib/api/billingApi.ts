import { apiRequest } from "@/lib/api/client";
import { isApiError } from "@/lib/api/apiError";
import { PAYOS_IDEMPOTENCY_KEY_PREFIX } from "@/lib/api/payOsIdempotencyKeyPrefix";

/** Trạng thái hóa đơn của BE; `VOID` = đã hủy (lịch giữ chỗ hết hạn hoặc bị hủy), không trả được nữa. */
export type BillingInvoiceStatus = "OPEN" | "PAID" | "VOID";

/** Thông điệp chữ thường (chưa có mã MSG) khi hóa đơn/lịch không còn thanh toán được. */
export const INVOICE_NOT_PAYABLE_MESSAGE =
  "Lịch hẹn hoặc hóa đơn này đã hết hạn hoặc không còn thanh toán được.";

/**
 * BE từ chối thanh toán vì hóa đơn không còn mở (đã hủy/hết hạn giữ lịch, hoặc đã trả đủ):
 * `INVOICE_NOT_PAYABLE` khi tạo mã thanh toán, `SOURCE_NOT_BILLABLE` khi lịch không còn tính phí được.
 * Lớp riêng để màn thanh toán phân biệt với lỗi mạng/máy chủ và không gợi ý "Tạo mã mới".
 */
export class InvoiceNotPayableError extends Error {
  constructor(options?: { cause?: unknown }) {
    super(INVOICE_NOT_PAYABLE_MESSAGE, options);
    this.name = "InvoiceNotPayableError";
  }
}

function notPayableOr(codes: readonly string[]) {
  return (cause: unknown): never => {
    if (
      isApiError(cause) &&
      cause.errorCode !== undefined &&
      codes.includes(cause.errorCode)
    )
      throw new InvoiceNotPayableError({ cause });
    throw cause;
  };
}

export interface BillingInvoice {
  id: string;
  invoiceNumber: string;
  appointmentId: string | null;
  visitId: string | null;
  status: BillingInvoiceStatus;
  totalAmount: number;
  paidAmount: number;
  outstandingBalance: number;
  issuedAt: string;
  items: Array<{
    lineNumber: number;
    description: string;
    quantity: number;
    unitPrice: number;
    lineTotal: number;
  }>;
}

export interface PayOsPaymentAttempt {
  id: string;
  invoiceId: string;
  channel: string;
  provider: string;
  amount: number;
  currency: string;
  status:
    | "PENDING"
    | "CONFIRMED"
    | "FAILED"
    | "CANCELLED"
    | "EXPIRED"
    | "RECONCILIATION_REQUIRED";
  checkoutUrl: string | null;
  /** VietQR payload from PayOS; render locally so payment data is not sent to a QR service. */
  qrCode: string | null;
  expiresAt: string | null;
  createdAt: string;
  confirmedAt: string | null;
  reconciliationReason: string | null;
  /** Thông tin chuyển khoản do PayOS trả khi tạo link; null với lần thanh toán cũ. */
  transferBankBin?: string | null;
  transferAccountNumber?: string | null;
  transferAccountName?: string | null;
  transferDescription?: string | null;
}

export interface BillingInvoicePage {
  page: number;
  size: number;
  hasMore: boolean;
  items: BillingInvoice[];
}

export function createAppointmentInvoice(
  appointmentId: string,
  signal?: AbortSignal,
): Promise<BillingInvoice> {
  return apiRequest<BillingInvoice>(
    `/api/v1/appointments/${encodeURIComponent(appointmentId)}/invoices`,
    { method: "POST", signal },
  )
    .catch(notPayableOr(["INVOICE_NOT_PAYABLE", "SOURCE_NOT_BILLABLE"]))
    .then((invoice) => {
      rememberAppointmentInvoice(appointmentId, invoice.id);
      return invoice;
    });
}

export function getBillingInvoice(
  invoiceId: string,
  signal?: AbortSignal,
): Promise<BillingInvoice> {
  return apiRequest<BillingInvoice>(
    `/api/v1/invoices/${encodeURIComponent(invoiceId)}`,
    { signal },
  );
}

export function listMyBillingInvoices(
  page: number,
  size = 20,
  signal?: AbortSignal,
): Promise<BillingInvoicePage> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return apiRequest<BillingInvoicePage>(`/api/v1/me/invoices?${params}`, {
    signal,
  });
}

export function createPayOsAttempt(
  invoiceId: string,
  idempotencyKey: string,
  signal?: AbortSignal,
): Promise<PayOsPaymentAttempt> {
  return apiRequest<PayOsPaymentAttempt>(
    `/api/v1/invoices/${encodeURIComponent(invoiceId)}/payment-attempts`,
    {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      signal,
    },
  ).catch(notPayableOr(["INVOICE_NOT_PAYABLE"]));
}

export function getPayOsAttempt(
  attemptId: string,
  signal?: AbortSignal,
): Promise<PayOsPaymentAttempt> {
  return apiRequest<PayOsPaymentAttempt>(
    `/api/v1/payment-attempts/${encodeURIComponent(attemptId)}`,
    { signal },
  );
}

export function reconcilePayOsAttempt(
  attemptId: string,
  signal?: AbortSignal,
): Promise<PayOsPaymentAttempt> {
  return apiRequest<PayOsPaymentAttempt>(
    `/api/v1/payment-attempts/${encodeURIComponent(attemptId)}/reconciliation`,
    { method: "POST", signal },
  );
}

const idempotencyStorageKey = (invoiceId: string) =>
  `${PAYOS_IDEMPOTENCY_KEY_PREFIX}${invoiceId}`;
const appointmentInvoiceStorageKey = (appointmentId: string) =>
  `otcms:payos:appointment-invoice:${appointmentId}`;

/** Ghi nhớ hóa đơn của lịch để xóa được khóa idempotency khi chỉ biết mã lịch. */
function rememberAppointmentInvoice(appointmentId: string, invoiceId: string) {
  try {
    window.sessionStorage.setItem(
      appointmentInvoiceStorageKey(appointmentId),
      invoiceId,
    );
  } catch {
    // sessionStorage không dùng được: bỏ qua, khóa cũ chỉ còn trong phiên.
  }
}

/** Xóa khóa idempotency PayOS của một hóa đơn (hóa đơn hết hạn/hủy, hoặc bắt đầu thanh toán mới). */
export function clearPayOsIdempotencyKey(invoiceId: string): void {
  try {
    window.sessionStorage.removeItem(idempotencyStorageKey(invoiceId));
  } catch {
    // Không có sessionStorage: không có gì để xóa.
  }
}

/**
 * Xóa khóa idempotency của hóa đơn thuộc một lịch (chỉ biết mã lịch, ví dụ khi giữ chỗ bị hủy) để
 * lần thanh toán sau không dính attempt cũ. An toàn khi chưa từng tạo hóa đơn cho lịch này.
 */
export function clearPayOsIdempotencyKeyForAppointment(
  appointmentId: string,
): void {
  try {
    const storageKey = appointmentInvoiceStorageKey(appointmentId);
    const invoiceId = window.sessionStorage.getItem(storageKey);
    if (invoiceId) clearPayOsIdempotencyKey(invoiceId);
    window.sessionStorage.removeItem(storageKey);
  } catch {
    // Không có sessionStorage: không có gì để xóa.
  }
}

export function payOsIdempotencyKey(invoiceId: string, renew = false): string {
  const storageKey = idempotencyStorageKey(invoiceId);
  try {
    if (!renew) {
      const existing = window.sessionStorage.getItem(storageKey);
      if (existing) return existing;
    }
    const key = window.crypto.randomUUID();
    window.sessionStorage.setItem(storageKey, key);
    return key;
  } catch {
    return window.crypto.randomUUID();
  }
}

export function createClinicalRoundInvoice(
  roundId: string,
): Promise<BillingInvoice> {
  return apiRequest<BillingInvoice>(
    `/api/v1/clinical-ordering-rounds/${encodeURIComponent(roundId)}/invoices`,
    { method: "POST" },
  );
}
export function recordCounterPayment(
  invoiceId: string,
  amount: number,
  method: "CASH" | "BANK_TRANSFER" | "OTHER",
  idempotencyKey: string,
) {
  return apiRequest<PayOsPaymentAttempt>(
    `/api/v1/invoices/${encodeURIComponent(invoiceId)}/counter-payments`,
    {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify({ amount, method }),
    },
  );
}
