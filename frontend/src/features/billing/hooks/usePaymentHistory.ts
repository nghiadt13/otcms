"use client";

import { useCallback, useEffect, useState } from "react";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { usePagedList } from "@/hooks/usePagedList";
import { isApiError } from "@/lib/api/apiError";
import type { PageQuery } from "@/lib/api/pagination";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import { listPayments } from "../api/paymentApi";
import { isBillingDateRangeValid } from "../schema/billingDateRangeRules";
import type {
  PaymentFilters,
  PaymentListItem,
  PaymentMethod,
  PaymentSummary,
} from "../types/payment";

/** Mặc định: hôm nay − 30 ngày → hôm nay (gồm cả hai đầu), tính theo giờ Việt Nam. */
export function defaultPaymentFilters(now: Date): PaymentFilters {
  const to = todayIsoDate(now);
  return { from: addDays(to, -30), to, q: "", method: "" };
}

/** Từ > đến → "MSG-54" (hiện dưới ô Đến); hợp lệ → null. */
export function paymentDateRangeError(
  from: string,
  to: string,
): "MSG-54" | null {
  return isBillingDateRangeValid(from, to) ? null : "MSG-54";
}

/**
 * Trạng thái màn lịch sử thanh toán. Khoảng ngày sai được chặn ở đây: giữ khoảng nháp
 * để ô ngày hiện đúng, nhưng không đẩy lên bộ lọc nên không gọi mạng.
 * `summary` do BE tính trên toàn kết quả lọc; FE không tự cộng.
 */
export function usePaymentHistory() {
  const [initialFilters] = useState(() => defaultPaymentFilters(new Date()));
  // 403: tải lại cũng vô ích nên màn không hiện "Thử lại".
  const [forbidden, setForbidden] = useState(false);
  const fetchPage = useCallback(
    async (query: PaymentFilters & PageQuery, signal: AbortSignal) => {
      try {
        const result = await listPayments(query, signal);
        setForbidden(false);
        return result;
      } catch (error) {
        setForbidden(isApiError(error) && error.status === 403);
        throw error;
      }
    },
    [],
  );
  const list = usePagedList<
    PaymentListItem,
    PaymentFilters,
    { summary: PaymentSummary }
  >(fetchPage, initialFilters);
  const { setFilters } = list;

  const [range, setRange] = useState({
    from: initialFilters.from,
    to: initialFilters.to,
  });
  const rangeError = paymentDateRangeError(range.from, range.to);

  // Ô ngày bị xóa trắng: giữ giá trị trước đó, không gọi API.
  const setDateRange = useCallback(
    (next: { from: string; to: string }) => {
      if (!next.from || !next.to) return;
      setRange(next);
      if (paymentDateRangeError(next.from, next.to) === null) setFilters(next);
    },
    [setFilters],
  );

  const setMethod = useCallback(
    (method: PaymentMethod | "") => setFilters({ method }),
    [setFilters],
  );

  const [searchText, setSearchText] = useState("");
  const debounced = useDebouncedValue(searchText.trim());
  const currentQ = list.filters.q;
  useEffect(() => {
    if (debounced !== currentQ) setFilters({ q: debounced });
  }, [debounced, currentQ, setFilters]);

  return {
    items: list.data?.items ?? [],
    summary: list.data?.summary ?? null,
    totalItems: list.data?.totalItems ?? 0,
    totalPages: list.data?.totalPages ?? 1,
    page: list.page,
    setPage: list.setPage,
    loading: list.loading,
    error: list.error,
    retryable: !forbidden,
    reload: list.reload,
    filters: list.filters,
    range,
    rangeError,
    setDateRange,
    setMethod,
    searchText,
    setSearchText,
    hasFilter: Boolean(list.filters.q || list.filters.method),
  };
}
