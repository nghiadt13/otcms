"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { isApiError } from "@/lib/api/apiError";
import { messageFromError } from "@/lib/messages/appMessages";
import { getActivityLog } from "../api/activityLogApi";
import type { ActivityLogDetail } from "../types/activityLog";

/**
 * Tải chi tiết theo `id` (null = chưa mở). 404 không phải lỗi hiển thị trong Sheet:
 * gọi `onNotFound` để màn báo toast và đóng Sheet. Lỗi khác giữ trong `error`.
 */
export function useActivityLogDetail(
  id: string | null,
  onNotFound: (error: unknown) => void,
) {
  const [data, setData] = useState<ActivityLogDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);
  const onNotFoundRef = useRef(onNotFound);
  useEffect(() => {
    onNotFoundRef.current = onNotFound;
  });

  useEffect(() => {
    if (!id) return;
    const controller = new AbortController();
    // eslint-disable-next-line react-hooks/set-state-in-effect -- bắt đầu tải là hệ quả của đổi id
    setLoading(true);
    setError(null);
    setData(null);
    getActivityLog(id, controller.signal)
      .then((detail) => {
        if (controller.signal.aborted) return;
        setData(detail);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setLoading(false);
        if (isApiError(err) && err.status === 404) {
          onNotFoundRef.current(err);
          return;
        }
        setError(messageFromError(err));
      });
    return () => controller.abort();
  }, [id, reloadToken]);

  const reload = useCallback(() => setReloadToken((n) => n + 1), []);
  return { data, loading, error, reload };
}
