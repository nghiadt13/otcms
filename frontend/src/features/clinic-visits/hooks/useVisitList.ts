"use client";

import { useCallback, useEffect, useState } from "react";
import { messageFromError } from "@/lib/messages/appMessages";
import { notifyError } from "@/lib/messages/notify";
import { listVisits } from "../api/visitApi";
import type { VisitSummary, VisitWhen } from "../types/visitProgress";

/** Cỡ trang danh sách lượt khám. "Xem thêm" tải trang kế và nối vào cuối. */
export const VISIT_PAGE_SIZE = 10;

interface ListState {
  items: VisitSummary[];
  /** Trang đã tải được (0-based). Chỉ tăng khi tải thành công. */
  page: number;
  totalPages: number;
  status: "loading" | "ready" | "error";
  /** Câu lỗi khi tải trang đầu; lỗi tải thêm chỉ hiện toast. */
  error: string | null;
  loadingMore: boolean;
}

const INITIAL_STATE: ListState = {
  items: [],
  page: 0,
  totalPages: 0,
  status: "loading",
  error: null,
  loadingMore: false,
};

/**
 * Danh sách lượt khám theo trang: trang đầu khi mount, "Xem thêm" nối trang kế, thử lại tải lại
 * từ đầu. Đổi tab hoặc người khám thì màn đặt `key` lại để hook bắt đầu từ trang đầu.
 */
export function useVisitList(when: VisitWhen, patientId?: string) {
  const [state, setState] = useState<ListState>(INITIAL_STATE);
  // Mỗi lần tải (đầu, thử lại, xem thêm) là một object mới để effect chạy đúng một lần.
  const [request, setRequest] = useState<{ page: number }>({ page: 0 });

  useEffect(() => {
    const controller = new AbortController();
    const { page } = request;
    listVisits(
      { when, patientId, page, size: VISIT_PAGE_SIZE },
      controller.signal,
    )
      .then((result) => {
        if (controller.signal.aborted) return;
        setState((current) => ({
          items:
            page === 0 ? result.items : [...current.items, ...result.items],
          page: result.page,
          totalPages: result.totalPages,
          status: "ready",
          error: null,
          loadingMore: false,
        }));
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        if (page === 0) {
          setState({
            ...INITIAL_STATE,
            status: "error",
            error: messageFromError(error),
          });
          return;
        }
        // Trang kế lỗi: giữ các dòng đã có, không đẩy số trang đi.
        setState((current) => ({ ...current, loadingMore: false }));
        notifyError(error);
      });
    return () => controller.abort();
  }, [when, patientId, request]);

  const hasMore = state.status === "ready" && state.page + 1 < state.totalPages;

  const loadMore = useCallback(() => {
    if (state.loadingMore || !hasMore) return;
    setState((current) => ({ ...current, loadingMore: true }));
    setRequest({ page: state.page + 1 });
  }, [state.loadingMore, state.page, hasMore]);

  const reload = useCallback(() => {
    setState(INITIAL_STATE);
    setRequest({ page: 0 });
  }, []);

  return {
    items: state.items,
    loading: state.status === "loading",
    error: state.status === "error" ? state.error : null,
    loadingMore: state.loadingMore,
    hasMore,
    loadMore,
    reload,
  };
}
