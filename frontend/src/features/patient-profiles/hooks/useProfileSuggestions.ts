"use client";

import { useCallback, useEffect, useState } from "react";
import { subscribeLinkedProfiles } from "@/lib/patient-scope/linkedProfilesMockStore";
import { listSuggestedProfiles } from "../api/patientProfileLinkApi";
import type { SuggestedProfile } from "../types/patientProfile";

/**
 * Hồ sơ tại phòng khám dùng email đã xác minh nhưng chưa gắn. Tự tải lại khi kho gắn đổi,
 * nên gắn một hồ sơ ở màn này thì gợi ý còn lại cập nhật ngay.
 */
export function useProfileSuggestions() {
  const [suggestions, setSuggestions] = useState<SuggestedProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    listSuggestedProfiles(controller.signal)
      .then(({ items }) => {
        if (controller.signal.aborted) return;
        setSuggestions(items);
        setError(null);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setError(err);
        setLoading(false);
      });
    return () => controller.abort();
  }, [reloadToken]);

  const reload = useCallback(() => setReloadToken((n) => n + 1), []);

  useEffect(() => subscribeLinkedProfiles(reload), [reload]);

  return { suggestions, loading, error, reload };
}
