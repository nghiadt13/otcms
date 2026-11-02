"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { isApiError } from "@/lib/api/apiError";
import { messageFromError } from "@/lib/messages/appMessages";

type Result<T> = { id: string; detail?: T; error?: unknown };

/**
 * Tải chi tiết một bản ghi cho Sheet (dùng chung cho thanh toán và khoản chưa thanh toán).
 * `id = null` thì không tải; đổi id thì bỏ kết quả của id cũ; hủy request khi đóng/đổi.
 */
export function useBillingDetail<T>(
  id: string | null,
  fetchDetail: (id: string, signal: AbortSignal) => Promise<T>,
) {
  const [result, setResult] = useState<Result<T> | null>(null);
  const [reloadToken, setReloadToken] = useState(0);
  const fetchRef = useRef(fetchDetail);
  useEffect(() => {
    fetchRef.current = fetchDetail;
  });

  useEffect(() => {
    if (!id) return;
    const controller = new AbortController();
    fetchRef.current(id, controller.signal).then(
      (detail) => {
        if (!controller.signal.aborted) setResult({ id, detail });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ id, error });
      },
    );
    return () => controller.abort();
  }, [id, reloadToken]);

  const current = id && result?.id === id ? result : null;
  const reload = useCallback(() => {
    setResult(null);
    setReloadToken((n) => n + 1);
  }, []);

  const failure = current?.error;
  return {
    detail: current?.detail ?? null,
    loading: Boolean(id) && current === null,
    error: failure ? messageFromError(failure) : null,
    /** Chỉ lỗi tạm (5xx, mất mạng) mới tải lại được; 403/404 thì vô ích. */
    retryable: failure ? !isApiError(failure) || failure.status >= 500 : false,
    reload,
  };
}
