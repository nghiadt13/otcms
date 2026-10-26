"use client";

import { useCallback, useEffect, useState } from "react";
import {
  getMedicalHistory,
  updateMedicalHistory,
} from "../api/medicalHistoryApi";
import type {
  MedicalHistory,
  MedicalHistoryUpdate,
} from "../types/patientProfile";

/**
 * Tiền sử của một hồ sơ. `save` đặt lại bản đang xem bằng bản vừa lưu (version mới). `refresh`
 * tải lại và trả về promise để màn đợi xong rồi mới dựng lại ô nhập.
 */
export function useMedicalHistory(patientId: string) {
  const [history, setHistory] = useState<MedicalHistory | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    const controller = new AbortController();
    getMedicalHistory(patientId, controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return;
        setHistory(data);
        setError(null);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setError(err);
        setLoading(false);
      });
    return () => controller.abort();
  }, [patientId]);

  const refresh = useCallback(async () => {
    try {
      const data = await getMedicalHistory(patientId);
      setHistory(data);
      setError(null);
    } catch (err) {
      setError(err);
    }
  }, [patientId]);

  const save = useCallback(
    async (next: MedicalHistoryUpdate) => {
      const saved = await updateMedicalHistory(patientId, next);
      setHistory(saved);
      return saved;
    },
    [patientId],
  );

  return { history, loading, error, refresh, save };
}
