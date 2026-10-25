"use client";

import { useCallback, useEffect, useState } from "react";
import { isApiError } from "@/lib/api/apiError";
import { messageFromError } from "@/lib/messages/appMessages";
import { getAnnouncement } from "../api/announcementApi";
import type { AnnouncementDetail } from "../types/announcement";

type Result = {
  key: string;
  detail: AnnouncementDetail | null;
  error: unknown;
};

/**
 * Chi tiết một thông báo cho Sheet. `unavailable` = 403/404: thông báo không còn hoặc
 * không đủ quyền, màn nên toast rồi đóng Sheet; lỗi khác hiện trong Sheet kèm Thử lại.
 */
export function useAnnouncementDetail(id: string | null) {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<Result | null>(null);
  const key = `${id}:${attempt}`;

  useEffect(() => {
    if (!id) return;
    const controller = new AbortController();
    getAnnouncement(id, controller.signal)
      .then((detail) => {
        if (!controller.signal.aborted)
          setResult({ key, detail, error: null });
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setResult({ key, detail: null, error });
      });
    return () => controller.abort();
  }, [id, key]);

  const retry = useCallback(() => setAttempt((n) => n + 1), []);
  const current = result?.key === key ? result : null;
  const error = current?.error ?? null;

  return {
    detail: current?.detail ?? null,
    loading: id !== null && current === null,
    error: error ? messageFromError(error) : null,
    unavailable:
      isApiError(error) && (error.status === 403 || error.status === 404),
    rawError: error,
    retry,
  };
}
