"use client";

import { useCallback, useEffect, useState } from "react";
import { messageFromError } from "@/lib/messages/appMessages";

export interface LoadedItems<T> {
  items: T[];
  /** Chỉ `true` ở lần tải đầu hoặc khi tải lại từ lỗi/Thử lại. */
  loading: boolean;
  /** Câu lỗi hiển thị được; `null` khi không lỗi. */
  error: string | null;
  reload: () => void;
}

/** Tải một danh sách `{ items }` một lần khi mount; `reload` tải lại. Huỷ request cũ khi tải lại. */
export function useLoadedItems<T>(
  load: (signal: AbortSignal) => Promise<{ items: T[] }>,
): LoadedItems<T> {
  const [state, setState] = useState<{
    items: T[];
    loading: boolean;
    error: string | null;
  }>({ items: [], loading: true, error: null });
  const [token, setToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal)
      .then(({ items }) => {
        if (controller.signal.aborted) return;
        setState({ items, loading: false, error: null });
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setState((current) => ({
          items: current.items,
          loading: false,
          error: messageFromError(err),
        }));
      });
    return () => controller.abort();
  }, [load, token]);

  const reload = useCallback(() => {
    setState((current) => ({ ...current, loading: true, error: null }));
    setToken((n) => n + 1);
  }, []);

  return { ...state, reload };
}
