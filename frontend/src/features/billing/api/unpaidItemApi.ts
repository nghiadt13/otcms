import { ApiError } from "@/lib/api/apiError";
import { clone, mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import { isBillingDateRangeValid } from "../schema/billingDateRangeRules";
import type {
  ServiceOption,
  UnpaidItem,
  UnpaidItemDetail,
  UnpaidItemListQuery,
  UnpaidItemPage,
} from "../types/unpaidItem";
import {
  isWithinDateRange,
  matchesKeyword,
  sumAmounts,
} from "./billingMockHelpers";
import {
  MOCK_SERVICE_OPTIONS,
  UNPAID_ITEM_ERROR_QUERY,
  UNPAID_ITEM_FORBIDDEN_ID,
  unpaidItemMockStore,
} from "./unpaidItemMockData";

// Lớp API giả cho /api/v1/admin/unpaid-items. Chỉ có hàm đọc (Admin không thu tiền).
// Chữ ký khớp phase-12 mục 7. Khi nối BE thật: thay thân hàm bằng `apiRequest`, gửi
// `toQueryString({ ...query, q: query.q?.trim(), sort: "orderedAt,asc" })`.

const DELAY_MS = 300;

const toListItem = (detail: UnpaidItemDetail): UnpaidItem => {
  const { orderedByDoctorName, ...item } = detail;
  void orderedByDoctorName; // chỉ có ở chi tiết
  return item;
};

export async function listUnpaidItems(
  query: UnpaidItemListQuery,
  signal?: AbortSignal,
): Promise<UnpaidItemPage> {
  await mockDelay(DELAY_MS, signal);
  if (!isBillingDateRangeValid(query.from, query.to)) mockFail(400, "MSG-54");
  const keyword = query.q?.trim() ?? "";
  if (keyword === UNPAID_ITEM_ERROR_QUERY) mockFail(500, "MSG-04");

  // FE luôn gửi sort=orderedAt,asc: khoản cũ lên đầu.
  const matched = unpaidItemMockStore.items
    .filter(
      (item) =>
        isWithinDateRange(item.orderedAt, query.from, query.to) &&
        (!query.kind || item.kind === query.kind) &&
        (!query.serviceId || item.serviceId === query.serviceId) &&
        matchesKeyword(keyword, item.patient.fullName, item.patient.phone),
    )
    .sort((a, b) => a.orderedAt.localeCompare(b.orderedAt));

  // Tổng tính trên toàn tập đã lọc, trước khi cắt trang.
  const totalPriceVnd = sumAmounts(matched, (item) => item.priceVnd);
  return clone(
    paginate(matched.map(toListItem), query, { summary: { totalPriceVnd } }),
  );
}

export async function getUnpaidItem(
  id: string,
  signal?: AbortSignal,
): Promise<UnpaidItemDetail> {
  await mockDelay(DELAY_MS, signal);
  if (id === UNPAID_ITEM_FORBIDDEN_ID) mockFail(403, "MSG-50");
  const found = unpaidItemMockStore.items.find((item) => item.id === id);
  if (!found) throw new ApiError(404, { code: "MSG-50" });
  // Sheet không có hành động trên bản ghi nên chi tiết trả allowedActions rỗng.
  return { ...clone(found), allowedActions: [] };
}

/** Không thuộc spec 12: nguồn danh sách dịch vụ thật nằm ở phase 07. */
export async function listUnpaidItemServiceOptions(
  signal?: AbortSignal,
): Promise<ServiceOption[]> {
  await mockDelay(DELAY_MS, signal);
  return MOCK_SERVICE_OPTIONS.map(({ id, name }) => ({ id, name }));
}
