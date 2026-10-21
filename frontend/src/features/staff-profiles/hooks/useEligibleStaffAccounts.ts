"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { messageFromError } from "@/lib/messages/appMessages";
import { listEligibleStaffAccounts } from "../api/staffProfileApi";
import type { EligibleStaffAccount } from "../types/staffProfile";

/**
 * Tài khoản đủ điều kiện (BR-09). Tải khi mount, tức khi Dialog tạo mở.
 * `onLoaded` chạy sau mỗi lần tải xong để màn kiểm tra tài khoản đã chọn.
 */
export function useEligibleStaffAccounts(
  onLoaded?: (accounts: EligibleStaffAccount[]) => void,
) {
  const [accounts, setAccounts] = useState<EligibleStaffAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [token, setToken] = useState(0);
  const onLoadedRef = useRef(onLoaded);
  useEffect(() => {
    onLoadedRef.current = onLoaded;
  });

  useEffect(() => {
    const controller = new AbortController();
    // eslint-disable-next-line react-hooks/set-state-in-effect -- bắt đầu tải khi mở Dialog hoặc bấm Thử lại
    setLoading(true);
    setError(null);
    listEligibleStaffAccounts(undefined, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return;
        setAccounts(result);
        setLoading(false);
        onLoadedRef.current?.(result);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setError(messageFromError(err));
        setLoading(false);
      });
    return () => controller.abort();
  }, [token]);

  const reload = useCallback(() => setToken((n) => n + 1), []);
  return { accounts, loading, error, reload };
}
