import { mockDelay, mockFail, clone, paginate } from "@/lib/api/mockServer";
import type { ItemsResponse, PageResponse } from "@/lib/api/pagination";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { isBillingDateRangeValid } from "../schema/billingDateRangeRules";
import type {
  CreateOnlinePaymentRequest,
  CreateOnlinePaymentResponse,
  PatientPayment,
  PatientPaymentListQuery,
  PayableSourceDetail,
  PayableSourceSummary,
  PayableSourcesQuery,
  PaymentItem,
  PaymentSourceKind,
} from "../types/patientBilling";
import { isWithinDateRange, sumAmounts } from "./billingMockHelpers";
import { FORBIDDEN_SOURCE_ID } from "./patientBillingMockData";
import {
  advanceStoredPayment,
  createStoredPayment,
  findStoredPayment,
  findStoredSource,
  isPaymentListFailing,
  isSourceListFailing,
  listStoredPayments,
  listStoredSources,
  lockedItemIds,
  type StoredPayment,
  type StoredSource,
} from "./patientBillingMockStore";
import { isSafeRedirectUrl } from "./patientBillingRedirect";

// LỚP API GIẢ cho khu bệnh nhân. Chữ ký khớp hợp đồng REST ở phase 06 §7; khi nối BE chỉ thay
// thân hàm bằng apiRequest. Mọi quyết định (tổng tiền, khoản được trả, kết quả giao dịch) là
// của BE: FE chỉ hiển thị. Không đọc tham số `vnp_*` từ URL.

export { resetPatientBillingMock } from "./patientBillingMockStore";

const LATENCY_MS = process.env.NODE_ENV === "test" ? 0 : 300;
// Độ trễ 0 ms trong test không đi qua bộ hẹn giờ: kết quả tới ngay, không phụ thuộc đồng hồ giả.
const latency = (signal?: AbortSignal) =>
  LATENCY_MS === 0 ? Promise.resolve() : mockDelay(LATENCY_MS, signal);

function summaryOf(source: StoredSource): PayableSourceSummary {
  const pending = source.items.filter((item) => item.status === "PENDING_PAYMENT");
  return {
    kind: source.kind,
    id: source.id,
    date: source.date,
    patient: { ...source.patient },
    pendingCount: pending.length,
    pendingAmount: sumAmounts(pending, (item) => item.amount),
  };
}

/** Bỏ `room` (chi tiết nội bộ của phòng khám) khỏi khoản gửi ra ngoài. */
function toPaymentItem(item: StoredSource["items"][number]): PaymentItem {
  return {
    id: item.id,
    label: item.label,
    amount: item.amount,
    status: item.status,
    cancellationReason: item.cancellationReason,
    receipt: item.receipt ? { ...item.receipt } : null,
  };
}

function toPatientPayment(payment: StoredPayment): PatientPayment {
  return {
    id: payment.id,
    status: payment.status,
    method: payment.method,
    total: payment.total,
    paidAt: payment.paidAt,
    vnpayRef: payment.vnpayRef,
    receiptNo: payment.receiptNo,
    patientName: payment.patient.fullName,
    items: payment.items.map((item) => ({ ...item })),
    // Số thứ tự chỉ có khi giao dịch đã thành công (UC55 bước 7).
    queueAssignments: payment.status === "SUCCESS" ? payment.queueAssignments.map((q) => ({ ...q })) : [],
    sourceKind: payment.sourceKind,
    sourceId: payment.sourceId,
  };
}

/** GET /api/v1/payments/payable-sources?patientId= → `{items}`, nguồn chờ thanh toán. */
export async function listPayableSources(
  query: PayableSourcesQuery = {},
  signal?: AbortSignal,
): Promise<ItemsResponse<PayableSourceSummary>> {
  await latency(signal);
  if (isSourceListFailing()) mockFail(500, "MSG-04");
  const items = listStoredSources()
    .map(summaryOf)
    .filter(
      (summary) =>
        summary.pendingCount > 0 && (!query.patientId || summary.patient.id === query.patientId),
    )
    .sort((a, b) => b.date.localeCompare(a.date));
  return clone({ items });
}

/** GET /api/v1/payments/payable-sources/{kind}/{id} */
export async function getPayableSource(
  kind: PaymentSourceKind,
  id: string,
  signal?: AbortSignal,
): Promise<PayableSourceDetail> {
  await latency(signal);
  if (id === FORBIDDEN_SOURCE_ID) mockFail(403, "MSG-40");
  const source = findStoredSource(kind, id) ?? mockFail(404, "MSG-41");
  return clone({
    ...summaryOf(source),
    items: source.items.map(toPaymentItem),
  });
}

/**
 * POST /api/v1/payments/online → 201. Trọn từng khoản PENDING_PAYMENT của MỘT nguồn; khoản đang
 * nằm trong giao dịch khác hoặc đã trả ở tab khác → 409 MSG-49.
 */
export async function createOnlinePayment(
  request: CreateOnlinePaymentRequest,
  signal?: AbortSignal,
): Promise<CreateOnlinePaymentResponse> {
  await latency(signal);
  const { itemIds } = request;
  // Trùng mã khoản sẽ cộng tiền hai lần: từ chối ngay (lỗi dữ liệu gửi lên).
  if (itemIds.length === 0 || new Set(itemIds).size !== itemIds.length)
    mockFail(400, "MSG-12", undefined, [{ field: "itemIds", code: "MSG-12" }]);
  if (request.sourceId === FORBIDDEN_SOURCE_ID) mockFail(403, "MSG-40");
  const source = findStoredSource(request.sourceKind, request.sourceId) ?? mockFail(404, "MSG-41");

  const locked = lockedItemIds();
  const eligible = itemIds.every((itemId) => {
    const item = source.items.find((candidate) => candidate.id === itemId);
    return item?.status === "PENDING_PAYMENT" && !locked.has(itemId);
  });
  if (!eligible) mockFail(409, "MSG-49");

  const payment = createStoredPayment(source, itemIds);
  const redirectUrl = `${PATIENT_ROUTES.paymentResult}?paymentId=${encodeURIComponent(payment.id)}`;
  // FE không tin đường dẫn lạ: BE chỉ được trả nội bộ hoặc máy chủ VNPay đã cấu hình.
  if (!isSafeRedirectUrl(redirectUrl)) mockFail(500, "MSG-04");
  return clone({ paymentId: payment.id, total: payment.total, redirectUrl });
}

/**
 * GET /api/v1/payments/{id}: đọc kết quả giao dịch. Mỗi lần đọc đẩy giao dịch một bước (kịch
 * bản PENDING rồi SUCCESS). 403/404 → MSG-50.
 */
export async function getPayment(id: string, signal?: AbortSignal): Promise<PatientPayment> {
  await latency(signal);
  const payment = findStoredPayment(id) ?? mockFail(404, "MSG-50");
  advanceStoredPayment(payment);
  return clone(toPatientPayment(payment));
}

/**
 * GET /api/v1/payments?status=SUCCESS&from&to&patientId&page&size: chỉ giao dịch đã hoàn tất.
 * `from > to` → 400 MSG-54 (BE kiểm lại dù FE đã chặn).
 */
export async function listMyPayments(
  query: PatientPaymentListQuery,
  signal?: AbortSignal,
): Promise<PageResponse<PatientPayment>> {
  await latency(signal);
  if (isPaymentListFailing()) mockFail(500, "MSG-04");
  if (query.from && query.to && !isBillingDateRangeValid(query.from, query.to))
    mockFail(400, "MSG-54", undefined, [{ field: "to", code: "MSG-54" }]);
  const matched = listStoredPayments()
    .filter(
      (payment) =>
        payment.status === "SUCCESS" &&
        isWithinDateRange(payment.paidAt ?? "", query.from, query.to) &&
        (!query.patientId || payment.patient.id === query.patientId),
    )
    .sort((a, b) => (b.paidAt ?? "").localeCompare(a.paidAt ?? ""));
  return clone(paginate(matched.map(toPatientPayment), query));
}
