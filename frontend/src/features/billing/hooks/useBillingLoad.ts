"use client";

import { useCallback, useEffect, useRef, useState } from "react";

export interface BillingLoadState<T> {
  data: T | null;
  loading: boolean;
  /** Lỗi thô (thường là ApiError) để nơi gọi đọc mã HTTP; `null` khi không lỗi. */
  error: Error | null;
  reload: () => void;
}

/** Chuẩn hóa lỗi bất kỳ thành `Error` để giữ mã HTTP khi có. */
export function toError(error: unknown): Error {
  return error instanceof Error ? error : new Error(String(error));
}

/**
 * Tải một tài nguyên khi mount, khi `key` đổi hoặc khi gọi `reload`. Đổi khóa hoặc rời màn thì
 * hủy request đang chạy, nên kết quả cũ không đè kết quả mới. Dùng cho lời gọi không phân trang.
 */
export function useBillingLoad<T>(
  load: (signal: AbortSignal) => Promise<T>,
  key: string,
): BillingLoadState<T> {
  const [state, setState] = useState<{
    data: T | null;
    loading: boolean;
    error: Error | null;
  }>({
    data: null,
    loading: true,
    error: null,
  });
  const [reloadToken, setReloadToken] = useState(0);
  // Giữ hàm tải mới nhất mà không chạy lại effect ở mỗi lần render.
  const loadRef = useRef(load);
  useEffect(() => {
    loadRef.current = load;
  });

  useEffect(() => {
    const controller = new AbortController();
    // eslint-disable-next-line react-hooks/set-state-in-effect -- bắt đầu tải là hệ quả của đổi khóa hoặc thử lại
    setState((current) => ({ ...current, loading: true, error: null }));
    loadRef
      .current(controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return;
        setState({ data, loading: false, error: null });
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        setState({ data: null, loading: false, error: toError(error) });
      });
    return () => controller.abort();
  }, [key, reloadToken]);

  const reload = useCallback(() => setReloadToken((count) => count + 1), []);
  return { ...state, reload };
}
