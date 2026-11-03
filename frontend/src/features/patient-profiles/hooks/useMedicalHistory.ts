"use client";

import { useCallback, useEffect, useState } from "react";
import { isApiError } from "@/lib/api/apiError";
import { AuthSessionError } from "@/lib/auth/authSession";
import {
  getMedicalHistory,
  updateMedicalHistory,
} from "../api/medicalHistoryApi";
import { isHiddenRecordError } from "../api/patientProfileErrors";
import type {
  MedicalHistory,
  MedicalHistoryUpdate,
} from "../types/patientProfile";

/** Client hiện ném Error thường khi không lấy được access token, trước khi gửi HTTP. */
function isMedicalHistoryAccessLost(failure: unknown): boolean {
  return (
    isHiddenRecordError(failure) ||
    (isApiError(failure) && failure.status === 401) ||
    (failure instanceof AuthSessionError &&
      (failure.status === 401 || failure.status === 403)) ||
    (failure instanceof Error &&
      failure.message === "Bạn cần đăng nhập để tiếp tục.")
  );
}

/** Màn mount theo key hồ sơ; chỉ thay bản đang xem sau response thành công. */
export function useMedicalHistory(patientId: string) {
  const [history, setHistory] = useState<MedicalHistory | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    const controller = new AbortController();
    getMedicalHistory(patientId, controller.signal).then(
      (data) => {
        if (controller.signal.aborted) return;
        setHistory(data);
        setError(null);
      },
      (failure: unknown) => {
        if (controller.signal.aborted) return;
        if (isMedicalHistoryAccessLost(failure)) setHistory(null);
        setError(failure);
      },
    );
    return () => controller.abort();
  }, [patientId]);

  const refresh = useCallback(async () => {
    try {
      const data = await getMedicalHistory(patientId);
      setHistory(data);
      setError(null);
      return data;
    } catch (failure) {
      // Mất xác thực hoặc quyền truy cập: xóa dữ liệu và để màn unmount editor.
      if (isMedicalHistoryAccessLost(failure)) {
        setHistory(null);
        setError(failure);
      }
      // Không nuốt lỗi: editor chỉ bỏ stale khi tải lại thành công.
      throw failure;
    }
  }, [patientId]);

  const save = useCallback(
    async (next: MedicalHistoryUpdate) => {
      try {
        const saved = await updateMedicalHistory(patientId, next);
        setHistory(saved);
        return saved;
      } catch (failure) {
        if (isMedicalHistoryAccessLost(failure)) {
          setHistory(null);
          setError(failure);
        }
        throw failure;
      }
    },
    [patientId],
  );

  return { history, loading: !history && !error, error, refresh, save };
}
