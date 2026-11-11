"use client";

import { useCallback, useEffect, useState } from "react";
import { getMyAppointment } from "../api/patientAppointmentSource";
import type { PatientAppointment } from "../types/patientAppointment";

interface DetailState {
  data: PatientAppointment | null;
  /** Lúc tải xong (epoch ms): mốc "đã tới giờ" và "hôm nay" của màn tính theo lúc này. */
  loadedAt: number;
  loading: boolean;
  /** Lỗi thô (thường là ApiError) để màn đọc mã MSG. */
  error: Error | null;
}

/**
 * Chi tiết một lịch của khu bệnh nhân. Tải khi mount và khi `id` đổi; `replace` nhận bản mới sau
 * một thao tác thành công (ví dụ hủy) để màn cập nhật ngay mà không tải lại.
 */
export function usePatientAppointment(id: string) {
  const [state, setState] = useState<DetailState>({
    data: null,
    loadedAt: 0,
    loading: true,
    error: null,
  });
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    getMyAppointment(id, controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return;
        setState({ data, loadedAt: Date.now(), loading: false, error: null });
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        setState({
          data: null,
          loadedAt: 0,
          loading: false,
          error: error instanceof Error ? error : new Error(String(error)),
        });
      });
    return () => controller.abort();
  }, [id, reloadToken]);

  const reload = useCallback(() => {
    setState((current) => ({ ...current, loading: true, error: null }));
    setReloadToken((count) => count + 1);
  }, []);

  useEffect(() => {
    const refreshOnFocus = () => {
      if (document.visibilityState === "visible") reload();
    };
    window.addEventListener("focus", refreshOnFocus);
    document.addEventListener("visibilitychange", refreshOnFocus);
    return () => {
      window.removeEventListener("focus", refreshOnFocus);
      document.removeEventListener("visibilitychange", refreshOnFocus);
    };
  }, [reload]);

  const replace = useCallback(
    (next: PatientAppointment) =>
      setState((current) => ({ ...current, data: next })),
    [],
  );

  return {
    appointment: state.data,
    loadedAt: state.loadedAt,
    loading: state.loading,
    error: state.error,
    reload,
    replace,
  };
}
