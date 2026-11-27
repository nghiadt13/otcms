"use client";

import { useCallback, useEffect, useState } from "react";
import { isApiError } from "@/lib/api/apiError";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import type { DashboardPeriod } from "@/features/dashboard/types/dashboardOverview";

/**
 * Tải một dữ liệu của Owner theo kỳ. Đổi kỳ thì hủy request cũ nên phản hồi muộn không
 * ghi đè kỳ mới; `data` giữ kết quả của lần tải thành công gần nhất.
 */
export function useOwnerPeriodData<T>(
  period: DashboardPeriod,
  load: (period: DashboardPeriod, signal: AbortSignal) => Promise<T>,
) {
  const { from, to } = period;
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    // eslint-disable-next-line react-hooks/set-state-in-effect -- bắt đầu tải là hệ quả của đổi kỳ
    setLoading(true);
    setError(null);
    load({ from, to }, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return;
        setData(result);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setError(
          isApiError(err) && err.status === 403
            ? formatAppMessage("MSG-50")
            : messageFromError(err),
        );
        setLoading(false);
      });
    return () => controller.abort();
  }, [from, to, load, reloadToken]);

  const retry = useCallback(() => setReloadToken((n) => n + 1), []);
  return { data, loading, error, retry };
}
