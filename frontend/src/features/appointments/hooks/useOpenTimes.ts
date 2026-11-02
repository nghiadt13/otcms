"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { getOpenTimes as getMockOpenTimes } from "../api/bookingApi";
import { getOpenTimes as getLiveOpenTimes } from "../api/bookingLiveApi";
import type { OpenTimesResponse } from "../types/booking";

export interface OpenTimesState {
  data: OpenTimesResponse | null;
  /** `true` khi đã có tham số nhưng chưa có kết quả của đúng tham số đó. */
  loading: boolean;
  failed: boolean;
  reload: () => void;
}

type Result =
  | { key: string; status: "ok"; data: OpenTimesResponse }
  | { key: string; status: "failed"; error: unknown };

/**
 * Giờ trống theo dịch vụ và người khám. Đổi tham số → hủy request cũ và bỏ kết quả cũ, nên màn
 * không bao giờ hiện giờ của dịch vụ/người khác. Tải lại giữ kết quả cũ cho tới khi có kết quả
 * mới. `onError` nhận lỗi thô (để khối quyết định MSG-29 bỏ chọn dịch vụ).
 */
export function useOpenTimes(
  serviceId: string | null,
  patientId: string | null,
  /** YYYY-MM-DD; bỏ trống thì BE dùng hôm nay. */
  from?: string,
  onError?: (error: unknown) => void,
  /** `true`: lấy từ backend thật (khu bệnh nhân). */
  live = false,
): OpenTimesState {
  const key =
    serviceId && patientId ? `${serviceId}|${patientId}|${from ?? ""}` : null;
  const [result, setResult] = useState<Result | null>(null);
  const [reloadToken, setReloadToken] = useState(0);
  // Giữ callback mới nhất mà không chạy lại effect mỗi lần render.
  const onErrorRef = useRef(onError);
  useEffect(() => {
    onErrorRef.current = onError;
  });

  useEffect(() => {
    if (!key || !serviceId || !patientId) return;
    const controller = new AbortController();
    (live ? getLiveOpenTimes : getMockOpenTimes)(
      { serviceId, patientId, from },
      controller.signal,
    )
      .then((data) => {
        if (controller.signal.aborted) return;
        setResult({ key, status: "ok", data });
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        setResult({ key, status: "failed", error });
        onErrorRef.current?.(error);
      });
    return () => controller.abort();
  }, [key, serviceId, patientId, from, reloadToken, live]);

  const reload = useCallback(() => setReloadToken((n) => n + 1), []);

  const current = key !== null && result?.key === key ? result : null;
  return {
    data: current?.status === "ok" ? current.data : null,
    loading: key !== null && current === null,
    failed: current?.status === "failed",
    reload,
  };
}
