"use client";

import { useEffect, useState } from "react";
import { isApiError } from "@/lib/api/apiError";
import { getExamQueueEntry, type ExamQueueItem } from "@/lib/api/examQueueApi";

interface QueueState {
  visitId: string;
  entry: ExamQueueItem | null;
  error: unknown;
}

/**
 * Dòng hàng chờ khám của lượt đã check-in (A6) cho khu bệnh nhân. Không có lượt thì không tải. 404 nghĩa
 * là lượt chưa vào hàng chờ: không phải lỗi, `entry` là null. Lỗi khác trả về `error` để màn hiển thị.
 */
export function usePatientExamQueueEntry(visitId: string | null): {
  entry: ExamQueueItem | null;
  error: unknown;
} {
  const [state, setState] = useState<QueueState | null>(null);

  useEffect(() => {
    if (visitId === null) return;
    let active = true;
    getExamQueueEntry(visitId)
      .then((entry) => {
        if (active) setState({ visitId, entry, error: null });
      })
      .catch((error: unknown) => {
        if (!active) return;
        const notQueued = isApiError(error) && error.status === 404;
        setState({ visitId, entry: null, error: notQueued ? null : error });
      });
    return () => {
      active = false;
    };
  }, [visitId]);

  // Kết quả của lượt khác, hoặc lượt đang tải, không được hiện: chỉ dùng khi đúng lượt đang xem.
  return state !== null && state.visitId === visitId
    ? { entry: state.entry, error: state.error }
    : { entry: null, error: null };
}
