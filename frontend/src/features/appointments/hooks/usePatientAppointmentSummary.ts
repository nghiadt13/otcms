"use client";

import { useCallback, useEffect, useState } from "react";
import { listMyAppointments } from "../api/patientAppointmentSource";

export interface PatientAppointmentSummary {
  /** Số lịch sắp tới. */
  upcoming: number;
  /** Số lịch sắp tới còn phí khám chưa thanh toán. */
  pendingPayment: number;
  /** Số lịch đã qua (mọi lịch không còn là Đã đặt còn hạn). */
  past: number;
}

/** Số lịch sắp tới đọc một lần để đếm "chờ thanh toán"; nhiều hơn mức này thì chỉ đếm trong mức đó. */
const UPCOMING_SAMPLE_SIZE = 50;

/**
 * Số liệu tóm tắt đầu trang Lịch hẹn: hai lần gọi danh sách có sẵn (không thêm endpoint). `null`
 * khi chưa tải hoặc tải lỗi: màn chỉ ẩn khối số liệu, danh sách bên dưới vẫn dùng bình thường.
 * Tải lại khi đổi người khám và khi quay lại cửa sổ, cùng nhịp với danh sách.
 */
export function usePatientAppointmentSummary(
  patientId?: string,
): PatientAppointmentSummary | null {
  const [summary, setSummary] = useState<PatientAppointmentSummary | null>(
    null,
  );
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      listMyAppointments(
        { when: "upcoming", patientId, page: 0, size: UPCOMING_SAMPLE_SIZE },
        controller.signal,
      ),
      listMyAppointments(
        { when: "past", patientId, page: 0, size: 1 },
        controller.signal,
      ),
    ])
      .then(([upcoming, past]) => {
        if (controller.signal.aborted) return;
        setSummary({
          upcoming: upcoming.totalItems,
          pendingPayment: upcoming.items.filter(
            (item) => item.examFee.status === "PENDING_PAYMENT",
          ).length,
          past: past.totalItems,
        });
      })
      .catch(() => {
        if (!controller.signal.aborted) setSummary(null);
      });
    return () => controller.abort();
  }, [patientId, reloadToken]);

  const reload = useCallback(() => setReloadToken((n) => n + 1), []);

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

  return summary;
}
