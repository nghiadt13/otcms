"use client";

import { useCallback, useEffect, useState } from "react";
import type { EligibleDoctor } from "../types/appointment";
import type { EligibleDoctorsApi } from "../types/reassignDoctorApi";

type Result = { key: string; doctors?: EligibleDoctor[]; error?: unknown };

/**
 * Bác sĩ có ca cùng giờ với một lịch hẹn, lấy qua `api` của khu đang đứng (admin hoặc Lễ tân).
 * Danh sách rỗng = luồng AF1.
 */
export function useEligibleDoctors(
  appointmentId: string,
  api: EligibleDoctorsApi,
) {
  const [result, setResult] = useState<Result | null>(null);
  const [token, setToken] = useState(0);
  const key = `${appointmentId}|${token}`;
  const { listEligibleDoctors } = api;

  useEffect(() => {
    const controller = new AbortController();
    listEligibleDoctors(appointmentId, controller.signal)
      .then((response) => {
        if (!controller.signal.aborted)
          setResult({ key, doctors: response.items });
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setResult({ key, error });
      });
    return () => controller.abort();
  }, [appointmentId, key, listEligibleDoctors]);

  const reload = useCallback(() => setToken((n) => n + 1), []);
  const current = result?.key === key ? result : null;

  return {
    doctors: current?.doctors ?? null,
    error: current?.error ?? null,
    loading: current === null,
    reload,
  };
}
