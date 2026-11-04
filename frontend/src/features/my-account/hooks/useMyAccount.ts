"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { usePathname } from "next/navigation";
import { peekSession } from "@/lib/auth/authSession";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { useAccountAvatar } from "./useAccountAvatar";
import { getMyAccount } from "../api/myAccountApi";
import {
  isMyAccountAccessError,
  myAccountErrorMessage,
} from "../api/myAccountErrors";
import {
  emitMyAccountAccessLost,
  MY_ACCOUNT_CHANGED,
  MY_ACCOUNT_ACCESS_LOST,
} from "../api/myAccountEvents";
import type { MyAccount } from "../types/myAccount";

export { emitMyAccountChanged } from "../api/myAccountEvents";

type LoadedAccount = {
  key: string;
  account: MyAccount | null;
  error: string | null;
};

/** Mỗi consumer có state riêng; event đồng bộ dữ liệu, không thêm shared cache. */
export function useMyAccount() {
  const pathname = usePathname();
  const [attempt, setAttempt] = useState(0);
  const key = `${pathname}:${attempt}`;
  const [loaded, setLoaded] = useState<LoadedAccount | null>(null);
  const generation = useRef(0);
  const request = useRef<AbortController | null>(null);

  const reload = useCallback(() => {
    generation.current++;
    request.current?.abort();
    setAttempt((value) => value + 1);
  }, []);

  useEffect(() => {
    const accessLost = () => {
      generation.current++;
      request.current?.abort();
      setLoaded({ key, account: null, error: formatAppMessage("MSG-14") });
    };
    const changed = (event: Event) => {
      const account = (event as CustomEvent<MyAccount | undefined>).detail;
      if (!account) {
        reload();
        return;
      }
      generation.current++;
      request.current?.abort();
      if (peekSession()?.accountId !== account.id) {
        accessLost();
        return;
      }
      setLoaded({ key, account, error: null });
    };
    window.addEventListener(MY_ACCOUNT_CHANGED, changed);
    window.addEventListener(MY_ACCOUNT_ACCESS_LOST, accessLost);
    return () => {
      window.removeEventListener(MY_ACCOUNT_CHANGED, changed);
      window.removeEventListener(MY_ACCOUNT_ACCESS_LOST, accessLost);
    };
  }, [key, reload]);

  useEffect(() => {
    const controller = new AbortController();
    request.current = controller;
    const version = ++generation.current;
    const accountIdAtStart = peekSession()?.accountId;
    getMyAccount(controller.signal).then(
      (account) => {
        if (controller.signal.aborted || version !== generation.current) return;
        if (peekSession()?.accountId !== account.id) {
          setLoaded({ key, account: null, error: formatAppMessage("MSG-14") });
          return;
        }
        setLoaded({ key, account, error: null });
      },
      (error: unknown) => {
        if (controller.signal.aborted || version !== generation.current) return;
        const currentId = peekSession()?.accountId;
        // Lỗi của request từ phiên trước không thu hồi dữ liệu của phiên mới.
        const anotherAccount =
          accountIdAtStart !== undefined &&
          currentId !== undefined &&
          currentId !== accountIdAtStart;
        if (!anotherAccount && isMyAccountAccessError(error)) {
          emitMyAccountAccessLost();
          return;
        }
        // Không giữ account cũ khi request mới thất bại hoặc phiên đã đổi/mất.
        setLoaded({ key, account: null, error: myAccountErrorMessage(error) });
      },
    );
    return () => controller.abort();
  }, [key]);

  const current = loaded?.key === key ? loaded : null;
  const wrongSession =
    current?.account && current.account.id !== peekSession()?.accountId;
  const account = wrongSession ? null : (current?.account ?? null);
  const avatar = useAccountAvatar(account);
  return {
    account: account ? { ...account, avatarUrl: avatar.avatarUrl } : null,
    avatarError: avatar.avatarError,
    loading: current === null,
    error: wrongSession ? formatAppMessage("MSG-14") : (current?.error ?? null),
    reload,
  };
}
