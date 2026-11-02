"use client";

import { useCallback, useEffect, useState } from "react";
import { getPatientStudy } from "../api/imagingStudyApi";
import type { PatientImagingStudy } from "../types/imagingStudy";

/**
 * Chi tiết một study. Lỗi giữ nguyên `ApiError` để màn quyết định: 403/404 → không tìm
 * thấy, còn lại → thử lại.
 */
export function usePatientStudy(studyId: string) {
  const [study, setStudy] = useState<PatientImagingStudy | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    // eslint-disable-next-line react-hooks/set-state-in-effect -- bắt đầu tải là hệ quả của đổi study hoặc bấm thử lại
    setLoading(true);
    setError(null);
    getPatientStudy(studyId, controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return;
        setStudy(data);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setError(err);
        setLoading(false);
      });
    return () => controller.abort();
  }, [studyId, reloadToken]);

  const reload = useCallback(() => setReloadToken((n) => n + 1), []);

  return { study, loading, error, reload };
}
