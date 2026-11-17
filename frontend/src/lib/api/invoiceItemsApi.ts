import { apiRequest } from "@/lib/api/client";
import type { PageResponse } from "@/lib/api/pagination";
import { toQueryString } from "@/lib/api/pagination";
import type { BillingInvoice } from "@/lib/api/billingApi";

// Khoản chưa thanh toán (UC-57) và hóa đơn theo bệnh nhân cho lễ tân (UC-53).
// Lọc sai ngày trả MSG-54 (400) từ BE; FE chặn trước ở form.

/** Khoản PENDING hoặc PAYMENT_PENDING (đang có mã thanh toán mở). */
export type UnpaidInvoiceItem = {
  itemId: string;
  invoiceId: string;
  invoiceNumber: string;
  patientProfileId: string;
  patientName: string;
  sourceType: string;
  visitId: string | null;
  appointmentId: string | null;
  serviceId: string | null;
  description: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  status: "PENDING" | "PAYMENT_PENDING";
  orderedAt: string;
};

export type UnpaidItemsQuery = {
  patientId?: string;
  serviceId?: string;
  /** yyyy-mm-dd */
  from?: string;
  /** yyyy-mm-dd */
  to?: string;
  page?: number;
  size?: number;
};

export function listUnpaidInvoiceItems(
  query: UnpaidItemsQuery,
  signal?: AbortSignal,
): Promise<PageResponse<UnpaidInvoiceItem>> {
  return apiRequest<PageResponse<UnpaidInvoiceItem>>(
    `/api/v1/invoice-items/unpaid${toQueryString({
      patientId: query.patientId,
      serviceId: query.serviceId,
      from: query.from,
      to: query.to,
      page: query.page ?? 0,
      size: query.size ?? 20,
    })}`,
    { signal },
  );
}

/** Hóa đơn của một hồ sơ bệnh nhân (lễ tân), hóa đơn còn khoản phải trả đứng đầu. */
export type ReceptionPatientBilling = {
  patientProfileId: string;
  patientName: string;
  invoices: PageResponse<BillingInvoice>;
};

export function getReceptionPatientBilling(
  profileId: string,
  page = 0,
  size = 20,
  signal?: AbortSignal,
): Promise<ReceptionPatientBilling> {
  return apiRequest<ReceptionPatientBilling>(
    `/api/v1/reception/patients/${encodeURIComponent(profileId)}/billing${toQueryString({ page, size })}`,
    { signal },
  );
}
