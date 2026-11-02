"use client";

import { useCallback, useEffect, useState } from "react";
import { getPatientProfile } from "../api/patientProfileApi";
import type { PatientProfileDetail } from "../types/patientProfile";

/**
 * Chi tiết một hồ sơ đã gắn. Lỗi giữ nguyên để màn quyết định: 403/404 → không tìm thấy,
 * lỗi khác → hiện câu và nút thử lại.
 */
export function usePatientProfile(profileId: string) {
  const [detail, setDetail] = useState<PatientProfileDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    getPatientProfile(profileId, controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return;
        setDetail(data);
        setError(null);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setError(err);
        setLoading(false);
      });
    return () => controller.abort();
  }, [profileId, reloadToken]);

  const reload = useCallback(() => setReloadToken((n) => n + 1), []);

  return { detail, loading, error, reload };
}
