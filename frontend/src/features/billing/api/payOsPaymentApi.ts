import { apiRequest } from "@/lib/api/client";

export interface BillingInvoice {
  id: string;
  invoiceNumber: string;
  appointmentId: string | null;
  visitId: string | null;
  status: string;
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
  );
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
  );
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

export function payOsIdempotencyKey(invoiceId: string, renew = false): string {
  const storageKey = `otcms:payos:idempotency:${invoiceId}`;
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
