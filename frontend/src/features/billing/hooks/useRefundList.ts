"use client";

import { useCallback } from "react";
import { usePagedList } from "@/hooks/usePagedList";
import type { PageQuery } from "@/lib/api/pagination";
import {
  listRefunds,
  type RefundItem,
  type RefundTab,
} from "@/lib/api/refundsApi";

type RefundFilters = { status: RefundTab };

/** Danh sách hoàn tiền thủ công (GET /api/v1/admin/refunds). Tab đổi thì về trang đầu. */
export function useRefundList() {
  const fetchPage = useCallback(
    (query: RefundFilters & PageQuery, signal: AbortSignal) =>
      listRefunds(
        { status: query.status, page: query.page, size: query.size },
        signal,
      ),
    [],
  );
  const list = usePagedList<RefundItem, RefundFilters>(fetchPage, {
    status: "PENDING",
  });
  const { setFilters } = list;

  const selectTab = useCallback(
    (status: RefundTab) => setFilters({ status }),
    [setFilters],
  );

  return {
    tab: list.filters.status,
    selectTab,
    items: list.data?.items ?? [],
    totalItems: list.data?.totalItems ?? 0,
    totalPages: list.data?.totalPages ?? 1,
    page: list.page,
    setPage: list.setPage,
    loading: list.loading,
    error: list.error,
    reload: list.reload,
  };
}
