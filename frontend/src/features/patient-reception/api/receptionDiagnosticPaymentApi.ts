import { apiRequest } from "@/lib/api/client";

export type PendingImagingOrder = {
  orderId: string;
  orderCode: string;
  visitId: string;
  patientProfileId: string;
  serviceId: string;
  serviceName: string;
  clinicalReason: string;
  bodyArea: string;
  unitPrice: number;
  orderedAt: string;
  invoiceId: string | null;
};

export type DiagnosticInvoice = {
  id: string;
  invoiceNumber: string;
  status: string;
  totalAmount: number;
  paidAmount: number;
  outstandingBalance: number;
  items: Array<{
    lineNumber: number;
    orderId: string | null;
    description: string;
    lineTotal: number;
  }>;
};

export function createAppointmentInvoice(appointmentId: string) {
  return apiRequest<DiagnosticInvoice>(
    `/api/v1/appointments/${encodeURIComponent(appointmentId)}/invoices`,
    { method: "POST" },
  );
}

export async function listPendingImagingOrders(
  patientProfileId?: string,
  signal?: AbortSignal,
) {
  const query = new URLSearchParams();
  if (patientProfileId) query.set("patientProfileId", patientProfileId);
  const queryString = query.toString();
  return apiRequest<PendingImagingOrder[]>(
    `/api/v1/reception/diagnostic-orders${queryString ? `?${queryString}` : ""}`,
    { signal },
  );
}

export function createDiagnosticInvoice(visitId: string, orderIds: string[]) {
  return apiRequest<DiagnosticInvoice>(
    `/api/v1/visits/${encodeURIComponent(visitId)}/diagnostic-invoices`,
    {
      method: "POST",
      headers: { "Idempotency-Key": crypto.randomUUID() },
      body: JSON.stringify({ orderIds }),
    },
  );
}

export function getDiagnosticInvoice(invoiceId: string) {
  return apiRequest<DiagnosticInvoice>(
    `/api/v1/invoices/${encodeURIComponent(invoiceId)}`,
  );
}

export function collectDiagnosticInvoice(
  invoice: DiagnosticInvoice,
  method: "CASH" | "BANK_TRANSFER" | "OTHER",
) {
  return apiRequest<unknown>(
    `/api/v1/invoices/${encodeURIComponent(invoice.id)}/counter-payments`,
    {
      method: "POST",
      headers: { "Idempotency-Key": crypto.randomUUID() },
      body: JSON.stringify({
        amount: Math.round(invoice.outstandingBalance),
        method,
      }),
    },
  );
}
