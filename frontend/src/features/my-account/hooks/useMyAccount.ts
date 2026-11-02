"use client";

import { useCallback, useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { messageFromError } from "@/lib/messages/appMessages";
import { getMyAccount } from "../api/myAccountApi";
import type { MyAccount } from "../types/myAccount";

const CHANGED_EVENT = "my-account:changed";

/** Báo cho mọi `useMyAccount` đang mount tải lại (sau khi đổi ảnh). */
export function emitMyAccountChanged() {
  window.dispatchEvent(new Event(CHANGED_EVENT));
}

/** Tài khoản đang đăng nhập; tải lại khi đổi route hoặc có sự kiện thay đổi. */
export function useMyAccount() {
  const pathname = usePathname();
  const [account, setAccount] = useState<MyAccount | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [token, setToken] = useState(0);

  useEffect(() => {
    const onChanged = () => setToken((n) => n + 1);
    window.addEventListener(CHANGED_EVENT, onChanged);
    return () => window.removeEventListener(CHANGED_EVENT, onChanged);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    getMyAccount(controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return;
        setAccount(result);
        setError(null);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        // Giữ dữ liệu đã có; chỉ báo lỗi.
        setError(messageFromError(err));
        setLoading(false);
      });
    return () => controller.abort();
  }, [pathname, token]);

  const reload = useCallback(() => {
    setLoading(true);
    setToken((n) => n + 1);
  }, []);

  return { account, loading, error, reload };
}
