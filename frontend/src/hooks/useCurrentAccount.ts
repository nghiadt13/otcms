"use client";

import { useCallback, useEffect, useState } from "react";
import { isApiError } from "@/lib/api/apiError";
import {
  fetchCurrentAccount,
  peekCurrentAccount,
  type CurrentAccount,
} from "@/lib/auth/currentAccountApi";

export type CurrentAccountState =
  | { status: "loading" }
  | { status: "anonymous" }
  | { status: "signedIn"; account: CurrentAccount }
  /** Có phiên nhưng không đọc được tài khoản (mạng, 5xx): khác "chưa đăng nhập". */
  | { status: "error" };

/**
 * Tài khoản đang đăng nhập (từ backend `GET /api/v1/me`). Chưa có phiên hoặc backend trả 401 là
 * `anonymous`; lỗi khác là `error` (ghi log, có `reload`). Tài khoản đã nhớ trong tab hiện ngay,
 * không qua `loading`. `signOutLocally` đưa về `anonymous` sau khi nơi gọi đã huỷ phiên.
 */
export function useCurrentAccount(): CurrentAccountState & {
  reload: () => void;
  signOutLocally: () => void;
} {
  const [state, setState] = useState<CurrentAccountState>(() => {
    const remembered = peekCurrentAccount();
    return remembered
      ? { status: "signedIn", account: remembered }
      : { status: "loading" };
  });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    fetchCurrentAccount()
      .then((account) => {
        if (!active) return;
        setState(
          account ? { status: "signedIn", account } : { status: "anonymous" },
        );
      })
      .catch((error: unknown) => {
        if (!active) return;
        if (isApiError(error) && error.status === 401) {
          setState({ status: "anonymous" });
          return;
        }
        console.error("Không đọc được tài khoản đang đăng nhập", error);
        setState({ status: "error" });
      });
    return () => {
      active = false;
    };
  }, [attempt]);

  const reload = useCallback(() => {
    setState({ status: "loading" });
    setAttempt((count) => count + 1);
  }, []);
  const signOutLocally = useCallback(
    () => setState({ status: "anonymous" }),
    [],
  );

  return { ...state, reload, signOutLocally };
}
