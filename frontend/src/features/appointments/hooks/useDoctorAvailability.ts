"use client";

import { useCallback, useEffect, useState } from "react";
import { getDoctorAvailability } from "../api/appointmentApi";
import type { DoctorAvailability } from "../types/appointment";

type Result = { key: string; data?: DoctorAvailability; error?: unknown };

/**
 * Tự tải lưới giờ trống khi đủ tham số (`enabled`). Kết quả gắn với khóa tham số
 * nên kết quả cũ không hiện cho tham số mới. `error` là lỗi thô: màn quyết định
 * hiển thị (toast, đỏ dưới ô, hay trong lưới).
 */
export function useDoctorAvailability(
  doctorId: string | undefined,
  from: string,
  to: string,
  enabled: boolean,
) {
  const [result, setResult] = useState<Result | null>(null);
  const [token, setToken] = useState(0);
  const key = enabled && doctorId ? `${doctorId}|${from}|${to}|${token}` : null;

  useEffect(() => {
    if (!enabled || !doctorId) return;
    const controller = new AbortController();
    const currentKey = `${doctorId}|${from}|${to}|${token}`;
    getDoctorAvailability(doctorId, { from, to }, controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setResult({ key: currentKey, data });
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setResult({ key: currentKey, error });
      });
    return () => controller.abort();
  }, [doctorId, from, to, token, enabled]);

  const reload = useCallback(() => setToken((n) => n + 1), []);
  const current = key && result?.key === key ? result : null;

  return {
    data: current?.data ?? null,
    error: current?.error ?? null,
    loading: key !== null && current === null,
    reload,
  };
}
