"use client";

import { useCallback, useEffect, useState } from "react";
import { getVisitResult } from "../api/examinationResultApi";
import type { ExaminationResult } from "../types/examinationResult";

/**
 * Kết quả một lượt khám. Lỗi giữ nguyên `ApiError` để màn quyết định:
 * 403/404 → không tìm thấy, 409 → quay về danh sách, còn lại → thử lại.
 */
export function useExaminationResult(visitId: string) {
  const [result, setResult] = useState<ExaminationResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    // eslint-disable-next-line react-hooks/set-state-in-effect -- bắt đầu tải là hệ quả của đổi lượt khám hoặc bấm thử lại
    setLoading(true);
    setError(null);
    getVisitResult(visitId, controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return;
        setResult(data);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setError(err);
        setLoading(false);
      });
    return () => controller.abort();
  }, [visitId, reloadToken]);

  const reload = useCallback(() => setReloadToken((n) => n + 1), []);

  return { result, loading, error, reload };
}
