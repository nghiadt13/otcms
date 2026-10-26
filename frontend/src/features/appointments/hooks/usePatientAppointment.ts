"use client";

import { useCallback, useEffect, useState } from "react";
import { getMyAppointment } from "../api/patientAppointmentApi";
import type { PatientAppointment } from "../types/patientAppointment";

interface DetailState {
  data: PatientAppointment | null;
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
    loading: true,
    error: null,
  });
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    getMyAppointment(id, controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return;
        setState({ data, loading: false, error: null });
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        setState({
          data: null,
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

  const replace = useCallback(
    (next: PatientAppointment) =>
      setState((current) => ({ ...current, data: next })),
    [],
  );

  return {
    appointment: state.data,
    loading: state.loading,
    error: state.error,
    reload,
    replace,
  };
}
