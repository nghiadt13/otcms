import { apiRequest } from "@/lib/api/client";
import type { PageResponse } from "@/lib/api/pagination";
import { toQueryString } from "@/lib/api/pagination";

// Hoàn tiền thủ công (admin/owner): khoản REFUND_PENDING chờ xác nhận, REFUNDED đã hoàn.
// Không gọi API hoàn tiền của PayOS; xác nhận chỉ ghi nhận sau khi phòng khám đã trả tiền ngoài hệ thống.

export type RefundStatus = "REFUND_PENDING" | "REFUNDED";

/** Tab của màn hình: PENDING = chờ hoàn, REFUNDED = đã hoàn. */
export type RefundTab = "PENDING" | "REFUNDED";

export type RefundItem = {
  itemId: string;
  invoiceId: string;
  invoiceNumber: string;
  patientId: string;
  patientName: string;
  description: string;
  amount: number;
  status: RefundStatus;
  reason: string | null;
  refundedAt: string | null;
  refundedByAccountId: string | null;
};

export function listRefunds(
  query: { status: RefundTab; page?: number; size?: number },
  signal?: AbortSignal,
): Promise<PageResponse<RefundItem>> {
  return apiRequest<PageResponse<RefundItem>>(
    `/api/v1/admin/refunds${toQueryString({
      status: query.status,
      page: query.page ?? 0,
      size: query.size ?? 20,
    })}`,
    { signal },
  );
}

/** Xác nhận đã hoàn tiền cho một khoản REFUND_PENDING. Ghi audit ở BE. */
export function confirmRefund(itemId: string, note?: string) {
  const trimmed = note?.trim();
  return apiRequest<RefundItem>(
    `/api/v1/invoice-items/${encodeURIComponent(itemId)}/refund-confirmation`,
    {
      method: "POST",
      body: JSON.stringify({ note: trimmed ? trimmed : null }),
    },
  );
}
