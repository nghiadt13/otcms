"use client";

import { useCallback, useEffect, useState } from "react";
import { listEligibleDoctors } from "../api/appointmentApi";
import type { EligibleDoctor } from "../types/appointment";

type Result = { key: string; doctors?: EligibleDoctor[]; error?: unknown };

/** Bác sĩ có ca cùng giờ với một lịch hẹn. Danh sách rỗng = luồng AF1. */
export function useEligibleDoctors(appointmentId: string) {
  const [result, setResult] = useState<Result | null>(null);
  const [token, setToken] = useState(0);
  const key = `${appointmentId}|${token}`;

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
  }, [appointmentId, key]);

  const reload = useCallback(() => setToken((n) => n + 1), []);
  const current = result?.key === key ? result : null;

  return {
    doctors: current?.doctors ?? null,
    error: current?.error ?? null,
    loading: current === null,
    reload,
  };
}
