"use client";

import { useCallback, useState } from "react";
import { usePagedList } from "@/hooks/usePagedList";
import {
  listUnpaidInvoiceItems,
  type UnpaidInvoiceItem,
  type UnpaidItemsQuery,
} from "@/lib/api/invoiceItemsApi";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import { isBillingDateRangeValid } from "../schema/billingDateRangeRules";

type UnpaidFilters = { from: string; to: string };

/**
 * Danh sách khoản chưa thanh toán (GET /api/v1/invoice-items/unpaid, UC-57). Mặc định 30 ngày gần
 * nhất. Từ > đến: không gọi mạng, báo MSG-54 (`rangeError`).
 */
export function useUnpaidInvoiceItemList() {
  const [initialFilters] = useState<UnpaidFilters>(() => {
    const to = todayIsoDate(new Date());
    return { from: addDays(to, -30), to };
  });
  const fetchPage = useCallback(
    (query: UnpaidItemsQuery & UnpaidFilters, signal: AbortSignal) =>
      listUnpaidInvoiceItems(
        { from: query.from, to: query.to, page: query.page, size: query.size },
        signal,
      ),
    [],
  );
  const list = usePagedList<UnpaidInvoiceItem, UnpaidFilters>(
    fetchPage,
    initialFilters,
  );
  const { setFilters } = list;
  const [range, setRange] = useState({
    from: initialFilters.from,
    to: initialFilters.to,
  });
  const rangeError = isBillingDateRangeValid(range.from, range.to)
    ? null
    : "MSG-54";

  const setDateRange = useCallback(
    (next: { from: string; to: string }) => {
      if (!next.from || !next.to) return;
      setRange(next);
      if (isBillingDateRangeValid(next.from, next.to)) setFilters(next);
    },
    [setFilters],
  );

  return {
    items: list.data?.items ?? [],
    totalItems: list.data?.totalItems ?? 0,
    totalPages: list.data?.totalPages ?? 1,
    page: list.page,
    setPage: list.setPage,
    loading: list.loading,
    error: list.error,
    reload: list.reload,
    range,
    rangeError,
    setDateRange,
  };
}
