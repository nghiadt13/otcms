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
  const [account, setAccount] = useState<CurrentPatientAccount | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshToken, setRefreshToken] = useState(0);

  useEffect(() => {
    const reload = () => setRefreshToken((current) => current + 1);
    window.addEventListener(CHANGED_EVENT, reload);
    return () => window.removeEventListener(CHANGED_EVENT, reload);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    getCurrentPatientAccount(controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return;
        setAccount(result);
        setError(null);
        setLoading(false);
      })
      .catch((cause: unknown) => {
        if (controller.signal.aborted) return;
        setError(messageFromError(cause));
        setLoading(false);
      });
    return () => controller.abort();
  }, [pathname, refreshToken]);

  return { account, loading, error };
}

