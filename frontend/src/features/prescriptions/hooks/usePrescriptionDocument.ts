"use client";

import { useCallback, useEffect, useState } from "react";
import { getPrescription } from "../api/prescriptionApi";
import type { PrescriptionDocument } from "../types/prescription";

/**
 * Một đơn thuốc. Lỗi giữ nguyên `ApiError` để màn quyết định: 403/404 → không tìm thấy,
 * 409 (chưa phát hành) → quay về danh sách, còn lại → thử lại.
 */
export function usePrescriptionDocument(prescriptionId: string) {
  const [prescription, setPrescription] = useState<PrescriptionDocument | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    // eslint-disable-next-line react-hooks/set-state-in-effect -- bắt đầu tải là hệ quả của đổi đơn hoặc bấm thử lại
    setLoading(true);
    setError(null);
    getPrescription(prescriptionId, controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return;
        setPrescription(data);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setError(err);
        setLoading(false);
      });
    return () => controller.abort();
  }, [prescriptionId, reloadToken]);

  const reload = useCallback(() => setReloadToken((n) => n + 1), []);

  return { prescription, loading, error, reload };
}
