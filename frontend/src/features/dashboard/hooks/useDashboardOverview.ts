"use client";

import { useCallback, useEffect, useState } from "react";
import { isApiError } from "@/lib/api/apiError";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import { getDashboardOverview } from "../api/dashboardApi";
import type {
  DashboardOverview,
  DashboardPeriod,
} from "../types/dashboardOverview";

export type DashboardFailure =
  /** 403: không đủ quyền, tải lại vô ích. */
  | { kind: "forbidden"; message: string }
  /** 400 MSG-72 từ BE: hiện dưới ô ngày, giữ số cũ. */
  | { kind: "invalidPeriod"; message: string }
  /** 5xx, mất mạng: hiện thông báo và Thử lại. */
  | { kind: "error"; message: string };

/**
 * Tải số liệu theo kỳ đang áp dụng. Đổi kỳ thì hủy request cũ nên phản hồi muộn
 * không ghi đè kỳ mới. `data` giữ số của lần tải thành công gần nhất.
 */
export function useDashboardOverview(period: DashboardPeriod) {
  const { from, to } = period;
  const [data, setData] = useState<DashboardOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [failure, setFailure] = useState<DashboardFailure | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    // eslint-disable-next-line react-hooks/set-state-in-effect -- bắt đầu tải là hệ quả của đổi kỳ
    setLoading(true);
    setFailure(null);
    getDashboardOverview({ from, to }, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return;
        setData(result);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setFailure(classify(err));
        setLoading(false);
      });
    return () => controller.abort();
  }, [from, to, reloadToken]);

  const retry = useCallback(() => setReloadToken((n) => n + 1), []);

  return { data, loading, failure, retry };
}

function classify(err: unknown): DashboardFailure {
  if (isApiError(err) && err.status === 403)
    return { kind: "forbidden", message: formatAppMessage("MSG-50") };
  if (isApiError(err) && err.status === 400 && err.code === "MSG-72")
    return { kind: "invalidPeriod", message: formatAppMessage("MSG-72") };
  return { kind: "error", message: messageFromError(err) };
}
