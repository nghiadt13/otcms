"use client";

import { useCallback, useEffect, useState } from "react";
import { messageFromError } from "@/lib/messages/appMessages";
import { listLinkedProfiles } from "@/lib/patient-scope/linkedProfilesApi";
import { subscribeLinkedProfiles } from "@/lib/patient-scope/linkedProfilesMockStore";
import type { LinkedProfile } from "@/lib/patient-scope/types";

export interface LinkedProfilesState {
  profiles: LinkedProfile[];
  /** Chỉ `true` ở lần tải đầu; tải lại không làm màn nhấp nháy về khung chờ. */
  loading: boolean;
  /** Câu lỗi hiển thị được; `null` khi không lỗi. */
  error: string | null;
  reload: () => void;
}

/**
 * Hồ sơ đã gắn của tài khoản (chỉ đọc). Tải khi mount và tự tải lại khi kho hồ sơ đổi,
 * nên mọi màn đang mở thấy thay đổi ngay mà không cần tải trang.
 */
export function useLinkedProfiles(): LinkedProfilesState {
  const [profiles, setProfiles] = useState<LinkedProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    listLinkedProfiles(controller.signal)
      .then(({ items }) => {
        if (controller.signal.aborted) return;
        setProfiles(items);
        setError(null);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setError(messageFromError(err));
        setLoading(false);
      });
    return () => controller.abort();
  }, [reloadToken]);

  const reload = useCallback(() => setReloadToken((n) => n + 1), []);

  useEffect(() => subscribeLinkedProfiles(reload), [reload]);

  return { profiles, loading, error, reload };
}
