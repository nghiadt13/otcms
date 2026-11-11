import { apiRequest } from "@/lib/api/client";

export type DiagnosticRefundRequest = {
  id: string;
  invoiceId: string;
  lineNumber: number;
  orderId: string;
  incidentId: string;
  reason: string;
  status: string;
  amount: number;
  requestedBy: string;
  approvedBy: string | null;
  decisionReason: string | null;
  providerReference: string | null;
  requestedAt: string;
};

export type ImagingRoomIncident = {
  incidentId: string;
  requestId: string;
  visitId: string;
  roomId: string;
  type: string;
  description: string;
  status: string;
  reportedBy: string;
  reportedAt: string;
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

export async function createDiagnosticInvoice(
  visitId: string,
  orderIds: string[],
) {
  return apiRequest<DiagnosticInvoice>(
    `/api/v1/visits/${encodeURIComponent(visitId)}/diagnostic-invoices`,
    {
      method: "POST",
      headers: { "Idempotency-Key": crypto.randomUUID() },
      body: JSON.stringify({ orderIds }),
    },
  );
}

export async function getDiagnosticInvoice(
  invoiceId: string,
  signal?: AbortSignal,
) {
  return apiRequest<DiagnosticInvoice>(
    `/api/v1/invoices/${encodeURIComponent(invoiceId)}`,
    { signal },
  );
}

export async function collectDiagnosticInvoice(
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

export async function listDiagnosticRefundRequests(signal?: AbortSignal) {
  return apiRequest<DiagnosticRefundRequest[]>(
    "/api/v1/admin/diagnostic-refund-requests?page=0&size=100",
    { signal },
  );
}

export async function decideDiagnosticRefund(
  id: string,
  decision: "APPROVE" | "REJECT",
  reason: string,
) {
  return apiRequest<DiagnosticRefundRequest>(
    `/api/v1/admin/diagnostic-refund-requests/${encodeURIComponent(id)}/decisions`,
    { method: "POST", body: JSON.stringify({ decision, reason }) },
  );
}

export async function listRoomIncidents(signal?: AbortSignal) {
  return apiRequest<ImagingRoomIncident[]>(
    "/api/v1/admin/room-incidents?page=0&size=100",
    { signal },
  );
}

export async function decideRoomIncident(
  id: string,
  decision: "CONFIRM" | "REJECT",
  reason: string,
) {
  return apiRequest<ImagingRoomIncident>(
    `/api/v1/admin/room-incidents/${encodeURIComponent(id)}/decisions`,
    { method: "POST", body: JSON.stringify({ decision, reason }) },
  );
}

export async function listApprovedDiagnosticRefunds(signal?: AbortSignal) {
  return apiRequest<DiagnosticRefundRequest[]>(
    "/api/v1/reception/diagnostic-refunds/approved?page=0&size=100",
    { signal },
  );
}

export async function recordDiagnosticRefundExecution(
  id: string,
  providerReference: string,
) {
  return apiRequest<DiagnosticRefundRequest>(
    `/api/v1/diagnostic-refund-requests/${encodeURIComponent(id)}/execution`,
    { method: "POST", body: JSON.stringify({ providerReference }) },
  );
}
