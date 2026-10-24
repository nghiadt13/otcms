"use client";

import { useCallback, useEffect, useState } from "react";
import { getBookingPatient } from "../api/staffAppointmentApi";
import type { BookingPatient } from "../types/booking";

type Result = { key: string; patient?: BookingPatient; error?: unknown };

/** Người đặt lịch theo mã từ `?patientId=`. Đổi mã hoặc `reload` thì tải lại; lỗi giữ nguyên để màn quyết định. */
export function useBookingPatient(patientId: string) {
  const [result, setResult] = useState<Result | null>(null);
  const [token, setToken] = useState(0);
  const key = `${patientId}|${token}`;

  useEffect(() => {
    const controller = new AbortController();
    getBookingPatient(patientId, controller.signal)
      .then((patient) => {
        if (!controller.signal.aborted) setResult({ key, patient });
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setResult({ key, error });
      });
    return () => controller.abort();
  }, [patientId, key]);

  const reload = useCallback(() => setToken((n) => n + 1), []);
  const current = result?.key === key ? result : null;

  return {
    patient: current?.patient ?? null,
    error: current?.error ?? null,
    loading: current === null,
    reload,
  };
}
