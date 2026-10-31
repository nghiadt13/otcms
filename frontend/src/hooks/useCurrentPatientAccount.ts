"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { messageFromError } from "@/lib/messages/appMessages";
import {
  getCurrentPatientAccount,
  type CurrentPatientAccount,
} from "@/lib/auth/currentPatientAccountApi";

const CHANGED_EVENT = "my-account:changed";

/** Reads the authenticated Patient identity used by the portal shell and profile hints. */
export function useCurrentPatientAccount() {
  const pathname = usePathname();
  const [refreshToken, setRefreshToken] = useState(0);
  const requestKey = `${pathname}:${refreshToken}`;
  const [loaded, setLoaded] = useState<{
    key: string;
    account: CurrentPatientAccount;
  } | null>(null);
  const [failure, setFailure] = useState<{
    key: string;
    message: string;
  } | null>(null);

  useEffect(() => {
    const reload = () => setRefreshToken((current) => current + 1);
    window.addEventListener(CHANGED_EVENT, reload);
    return () => window.removeEventListener(CHANGED_EVENT, reload);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    getCurrentPatientAccount(controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return;
        setLoaded({ key: requestKey, account: result });
        setFailure(null);
      })
      .catch((cause: unknown) => {
        if (controller.signal.aborted) return;
        setFailure({ key: requestKey, message: messageFromError(cause) });
      });
    return () => controller.abort();
  }, [requestKey]);

  const account = loaded?.key === requestKey ? loaded.account : null;
  const error = failure?.key === requestKey ? failure.message : null;
  return { account, loading: !account && !error, error };
}

