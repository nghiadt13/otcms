"use client";

import { useEffect, useRef } from "react";
import { peekSession } from "@/lib/auth/authSession";

/** Ref chặn submit lặp trước render và bỏ callback sau unmount/đổi tài khoản. */
export function useAccountOperation() {
  const mounted = useRef(false);
  const request = useRef<AbortController | null>(null);
  const owner = useRef<string | undefined>(undefined);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      request.current?.abort();
    };
  }, []);
  return {
    begin() {
      if (!mounted.current || request.current) return null;
      owner.current = peekSession()?.accountId;
      const controller = new AbortController();
      request.current = controller;
      return controller.signal;
    },
    isCurrent() {
      return (
        mounted.current &&
        !request.current?.signal.aborted &&
        owner.current === peekSession()?.accountId
      );
    },
    finish() {
      request.current = null;
    },
  };
}
