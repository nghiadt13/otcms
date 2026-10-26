"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { messageFromError } from "@/lib/messages/appMessages";
import type { PageQuery, PageResponse } from "@/lib/api/pagination";

type Fetcher<T, F, M> = (
  query: F & PageQuery,
  signal: AbortSignal,
) => Promise<PageResponse<T, M>>;

/**
 * Trạng thái một màn danh sách phân trang. Đổi bộ lọc → về trang 0 và huỷ request
 * cũ, nên kết quả cũ không bao giờ đè kết quả mới.
 * `data` giữ cả dữ liệu kèm trang (`summary`, `counts`).
 */
export function usePagedList<T, F extends object, M = object>(
  fetcher: Fetcher<T, F, M>,
  initialFilters: F,
  pageSize?: number,
) {
  const [filters, setFiltersState] = useState<F>(initialFilters);
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageResponse<T, M> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);
  // Giữ fetcher mới nhất mà không buộc effect chạy lại mỗi lần render.
  const fetcherRef = useRef(fetcher);
  useEffect(() => {
    fetcherRef.current = fetcher;
  });

  useEffect(() => {
    const controller = new AbortController();
    // eslint-disable-next-line react-hooks/set-state-in-effect -- bắt đầu tải là hệ quả của đổi bộ lọc/trang
    setLoading(true);
    setError(null);
    fetcherRef
      .current(
        { ...filters, page, size: pageSize } as F & PageQuery,
        controller.signal,
      )
      .then((result) => {
        if (controller.signal.aborted) return;
        setData(result);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setError(messageFromError(err));
        setLoading(false);
      });
    return () => controller.abort();
  }, [filters, page, pageSize, reloadToken]);

  const setFilters = useCallback((next: Partial<F>) => {
    setFiltersState((current) => ({ ...current, ...next }));
    setPage(0);
  }, []);

  const reload = useCallback(() => setReloadToken((n) => n + 1), []);

  /** Thay một bản ghi tại chỗ (sau khi đổi trạng thái) mà không tải lại cả trang. */
  const replaceItem = useCallback(
    (match: (item: T) => boolean, next: T) =>
      setData((current) =>
        current
          ? {
              ...current,
              items: current.items.map((item) => (match(item) ? next : item)),
            }
          : current,
      ),
    [],
  );

  return {
    data,
    loading,
    error,
    filters,
    setFilters,
    page,
    setPage,
    reload,
    replaceItem,
  };
}
