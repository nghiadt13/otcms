"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { notifyError } from "@/lib/messages/notify";
import { getVisitProgress } from "../api/visitApi";
import type { VisitProgress } from "../types/visitProgress";

/** Chu kỳ tự tải lại tiến độ khi tab đang hiện (chỉ trang chi tiết và trang chủ). */
export const VISIT_PROGRESS_REFRESH_MS = 30_000;

export interface VisitProgressState {
  /** Dữ liệu gần nhất tải được. Giữ nguyên khi tải lại lỗi. */
  progress: VisitProgress | null;
  /** `true` chỉ ở lần tải đầu; tải lại không đưa màn về khung chờ. */
  loading: boolean;
  /** Lỗi của lần tải đầu (chưa có dữ liệu nào). */
  error: unknown;
  /** Thời điểm lần tự tải lại gần nhất bị lỗi; `null` khi lần gần nhất thành công. */
  refreshFailedAt: Date | null;
  /** Đang tải lại theo yêu cầu người dùng. */
  refreshing: boolean;
  reload: () => void;
}

/**
 * Tiến độ một lượt khám (chỉ đọc). Tự tải lại mỗi 30 giây khi tab đang hiện; tab ẩn thì dừng,
 * hiện lại thì tải ngay. Tải lại tự động lỗi chỉ ghi thời điểm lỗi, không toast lặp; bấm
 * "Làm mới" lỗi thì báo toast. Mọi lần lỗi đều giữ dữ liệu cũ.
 */
export function useVisitProgress(visitId: string): VisitProgressState {
  const [progress, setProgress] = useState<VisitProgress | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [refreshFailedAt, setRefreshFailedAt] = useState<Date | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const inFlight = useRef<AbortController | null>(null);
  // Đã có dữ liệu hay chưa: quyết định lỗi đi vào khung lỗi hay chỉ là một lần tải lại hỏng.
  const hasData = useRef(false);

  // Mỗi lần tải hủy lần đang chạy, nên kết quả cũ không bao giờ đè kết quả mới.
  const load = useCallback(
    (manual: boolean) => {
      inFlight.current?.abort();
      const controller = new AbortController();
      inFlight.current = controller;
      getVisitProgress(visitId, controller.signal)
        .then((next) => {
          if (controller.signal.aborted) return;
          hasData.current = true;
          setProgress(next);
          setError(null);
          setRefreshFailedAt(null);
          setLoading(false);
          setRefreshing(false);
        })
        .catch((err: unknown) => {
          if (controller.signal.aborted) return;
          setLoading(false);
          setRefreshing(false);
          if (!hasData.current) {
            setError(err);
          } else if (manual) {
            notifyError(err);
          } else {
            setRefreshFailedAt(new Date());
          }
        });
    },
    [visitId],
  );

  useEffect(() => {
    let timer: ReturnType<typeof setInterval> | null = null;
    const stop = () => {
      if (timer !== null) clearInterval(timer);
      timer = null;
    };
    const start = () => {
      if (timer === null)
        timer = setInterval(() => load(false), VISIT_PROGRESS_REFRESH_MS);
    };
    const onVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        load(false);
        start();
      } else {
        stop();
      }
    };

    load(false);
    if (document.visibilityState === "visible") start();
    document.addEventListener("visibilitychange", onVisibilityChange);
    return () => {
      stop();
      document.removeEventListener("visibilitychange", onVisibilityChange);
      inFlight.current?.abort();
    };
  }, [load]);

  const reload = useCallback(() => {
    if (!hasData.current) {
      setError(null);
      setLoading(true);
    }
    setRefreshing(true);
    load(true);
  }, [load]);

  return { progress, loading, error, refreshFailedAt, refreshing, reload };
}
