"use client";

import { useCallback, useEffect, useState } from "react";
import { apiRequest } from "@/lib/api/client";
import { addDays } from "@/lib/format/formatDate";
import { vnIsoOf } from "../schema/bookingTime";
import type { DoctorAvailability } from "../types/appointment";

type Result = { key: string; data?: DoctorAvailability; error?: unknown };

/**
 * Tự tải lưới giờ trống khi đủ tham số (`enabled`). Kết quả gắn với khóa tham số
 * nên kết quả cũ không hiện cho tham số mới. `error` là lỗi thô: màn quyết định
 * hiển thị (toast, đỏ dưới ô, hay trong lưới).
 */
export function useDoctorAvailability(
  doctorId: string | undefined,
  serviceId: string | undefined,
  from: string,
  to: string,
  enabled: boolean,
) {
  const [result, setResult] = useState<Result | null>(null);
  const [token, setToken] = useState(0);
  const key =
    enabled && doctorId && serviceId
      ? `${doctorId}|${serviceId}|${from}|${to}|${token}`
      : null;

  useEffect(() => {
    if (!enabled || !doctorId || !serviceId) return;
    const controller = new AbortController();
    const currentKey = `${doctorId}|${serviceId}|${from}|${to}|${token}`;
    const params = new URLSearchParams({ doctorId, serviceId, from, to });
    apiRequest<{ slots: { startTime: string }[] }>(
      `/api/v1/staff/appointments/availability?${params}`,
      { signal: controller.signal },
    )
      .then(({ slots }): DoctorAvailability => {
        const starts = slots.map((slot) => vnIsoOf(new Date(slot.startTime)));
        const days = [];
        for (let date = from; date <= to; date = addDays(date, 1)) {
          const matching = starts.filter((start) => start.startsWith(date));
          days.push({
            date,
            hasShift: matching.length > 0,
            slots: matching.map((startAt) => ({
              startAt,
              status: "FREE" as const,
            })),
          });
        }
        return { doctorId, from, to, slotMinutes: 15, days };
      })
      .then((data) => {
        if (!controller.signal.aborted) setResult({ key: currentKey, data });
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setResult({ key: currentKey, error });
      });
    return () => controller.abort();
  }, [doctorId, serviceId, from, to, token, enabled]);

  const reload = useCallback(() => setToken((n) => n + 1), []);
  const current = key && result?.key === key ? result : null;

  return {
    data: current?.data ?? null,
    error: current?.error ?? null,
    loading: key !== null && current === null,
    reload,
  };
}
