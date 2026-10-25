import { ApiError } from "@/lib/api/apiError";
import { clone, mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import { isBillingDateRangeValid } from "../schema/billingDateRangeRules";
import type {
  PaymentDetail,
  PaymentListQuery,
  PaymentPageResponse,
} from "../types/payment";
import {
  isWithinDateRange,
  matchesKeyword,
  sumAmounts,
} from "./billingMockHelpers";
import {
  PAYMENT_ERROR_ID,
  PAYMENT_FORBIDDEN_ID,
  PAYMENT_SEED,
  toPaymentListItem,
} from "./paymentMockData";

// Lớp API giả cho /api/v1/admin/payments. Chỉ có hàm đọc (màn chỉ xem).
// Chữ ký khớp phase-11 mục 7: khi nối BE thật chỉ thay thân hàm bằng `apiRequest`.

const DELAY_MS = 300;
/** `q` này kích hoạt lỗi dịch vụ giả để thử trạng thái lỗi tải. */
export const PAYMENT_ERROR_QUERY = "ERR-500";

export async function listPayments(
  query: PaymentListQuery,
  signal?: AbortSignal,
): Promise<PaymentPageResponse> {
  await mockDelay(DELAY_MS, signal);
  if (query.from && query.to && !isBillingDateRangeValid(query.from, query.to))
    mockFail(400, "MSG-54", undefined, [{ field: "to", code: "MSG-54" }]);
  const keyword = query.q?.trim() ?? "";
  if (keyword === PAYMENT_ERROR_QUERY) mockFail(500, "MSG-04");

  const matched = PAYMENT_SEED.filter(
    (payment) =>
      isWithinDateRange(payment.paidAt, query.from, query.to) &&
      (!query.method || payment.method === query.method) &&
      matchesKeyword(keyword, payment.patientName, payment.receiptNumber),
  ).sort((a, b) => b.paidAt.localeCompare(a.paidAt));

  // Tổng tính trên toàn tập đã lọc, trước khi cắt trang.
  const cashAmount = sumAmounts(
    matched.filter((p) => p.method === "CASH"),
    (p) => p.amount,
  );
  const vnpayAmount = sumAmounts(
    matched.filter((p) => p.method === "VNPAY"),
    (p) => p.amount,
  );
  return clone(
    paginate(matched.map(toPaymentListItem), query, {
      summary: {
        totalAmount: cashAmount + vnpayAmount,
        cashAmount,
        vnpayAmount,
      },
    }),
  );
}

export async function getPaymentDetail(
  id: string,
  signal?: AbortSignal,
): Promise<PaymentDetail> {
  await mockDelay(DELAY_MS, signal);
  if (id === PAYMENT_FORBIDDEN_ID) mockFail(403, "MSG-50");
  if (id === PAYMENT_ERROR_ID) mockFail(500, "MSG-04");
  const found = PAYMENT_SEED.find((payment) => payment.id === id);
  if (!found) throw new ApiError(404, { code: "MSG-50" });
  return clone(found);
}
