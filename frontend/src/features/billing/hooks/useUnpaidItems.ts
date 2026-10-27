"use client";

import { useEffect, useState } from "react";
import { usePagedList } from "@/hooks/usePagedList";
import { messageFromError } from "@/lib/messages/appMessages";
import {
  getUnpaidItem,
  listUnpaidItemServiceOptions,
  listUnpaidItems,
} from "../api/unpaidItemApi";
import type {
  ServiceOption,
  UnpaidItem,
  UnpaidItemDetail,
  UnpaidItemFilter,
  UnpaidItemSummary,
} from "../types/unpaidItem";
import { useBillingDetail } from "./useBillingDetail";

/** Danh sách khoản chưa thanh toán (phân trang phía server, `summary` do BE tính toàn kỳ lọc). */
export function useUnpaidItems() {
  const list = usePagedList<
    UnpaidItem,
    UnpaidItemFilter,
    { summary: UnpaidItemSummary }
  >(listUnpaidItems, {});
  const { filters } = list;
  return {
    items: list.data?.items ?? [],
    summary: list.data?.summary ?? null,
    totalItems: list.data?.totalItems ?? 0,
    totalPages: list.data?.totalPages ?? 1,
    page: list.page,
    setPage: list.setPage,
    filters,
    setFilters: list.setFilters,
    loading: list.loading,
    error: list.error,
    reload: list.reload,
    hasFilter: Boolean(
      filters.from ||
      filters.to ||
      filters.q ||
      filters.serviceId ||
      filters.kind,
    ),
  };
}

/** Chi tiết một khoản cho Sheet. `id = null` thì không tải. */
export function useUnpaidItemDetail(id: string | null) {
  return useBillingDetail<UnpaidItemDetail>(id, getUnpaidItem);
}

/** Tùy chọn cho ô Dịch vụ. Lỗi tải chỉ khóa ô này, bảng vẫn chạy bình thường. */
export function useUnpaidItemServiceOptions() {
  const [state, setState] = useState<{
    options: ServiceOption[];
    error: string | null;
  }>({ options: [], error: null });

  useEffect(() => {
    const controller = new AbortController();
    listUnpaidItemServiceOptions(controller.signal).then(
      (options) => {
        if (!controller.signal.aborted) setState({ options, error: null });
      },
      (error: unknown) => {
        if (!controller.signal.aborted)
          setState({ options: [], error: messageFromError(error) });
      },
    );
    return () => controller.abort();
  }, []);

  return state;
}
