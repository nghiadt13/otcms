"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { peekSession } from "@/lib/auth/authSession";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { getOwnPatientProfile } from "../api/ownPatientProfileApi";
import {
  isOwnProfileAccessLost,
  isOwnProfileMissing,
  ownProfileErrorMessage,
} from "../api/ownPatientProfileErrors";
import type { OwnPatientProfile } from "../types/ownPatientProfile";

export type OwnProfileState =
  | { status: "loading" }
  | { status: "missing" }
  | { status: "error"; message: string }
  | { status: "ready"; detail: OwnPatientProfile };

type LoadedState = {
  key: number;
  state: OwnProfileState;
  sessionId?: string | null;
};
const MY_ACCOUNT_CHANGED = "my-account:changed";
const MY_ACCOUNT_ACCESS_LOST = "my-account:access-lost";

/** Loads the caller's profile only; no linked-profile list or inferred SELF record is used. */
export function useOwnPatientProfile() {
  const [attempt, setAttempt] = useState(0);
  const [loaded, setLoaded] = useState<LoadedState>({
    key: -1,
    state: { status: "loading" },
  });
  const requestGeneration = useRef(0);
  const attemptRef = useRef(attempt);

  useEffect(() => {
    const controller = new AbortController();
    const generation = ++requestGeneration.current;
    const key = attempt;
    const sessionIdAtStart = peekSession()?.accountId ?? null;
    getOwnPatientProfile(controller.signal).then(
      (detail) => {
        if (
          controller.signal.aborted ||
          generation !== requestGeneration.current ||
          sessionIdAtStart !== (peekSession()?.accountId ?? null)
        )
          return;
        setLoaded({
          key,
          state: { status: "ready", detail },
          sessionId: peekSession()?.accountId ?? null,
        });
      },
      (error: unknown) => {
        if (
          controller.signal.aborted ||
          generation !== requestGeneration.current ||
          sessionIdAtStart !== (peekSession()?.accountId ?? null)
        )
          return;
        setLoaded({
          key,
          state: isOwnProfileAccessLost(error)
            ? { status: "error", message: ownProfileErrorMessage(error) }
            : isOwnProfileMissing(error)
              ? { status: "missing" }
              : { status: "error", message: ownProfileErrorMessage(error) },
        });
      },
    );
    return () => controller.abort();
  }, [attempt]);

  useEffect(() => {
    const accountChanged = () => {
      requestGeneration.current++;
      const next = attemptRef.current + 1;
      attemptRef.current = next;
      setLoaded({ key: next, state: { status: "loading" } });
      setAttempt(next);
    };
    const accessLost = () => {
      requestGeneration.current++;
      setLoaded({
        key: attemptRef.current,
        state: { status: "error", message: formatAppMessage("MSG-14") },
      });
    };
    window.addEventListener(MY_ACCOUNT_CHANGED, accountChanged);
    window.addEventListener(MY_ACCOUNT_ACCESS_LOST, accessLost);
    return () => {
      window.removeEventListener(MY_ACCOUNT_CHANGED, accountChanged);
      window.removeEventListener(MY_ACCOUNT_ACCESS_LOST, accessLost);
    };
  }, []);

  const reload = useCallback(() => {
    const next = attemptRef.current + 1;
    attemptRef.current = next;
    setLoaded({ key: next, state: { status: "loading" } });
    setAttempt(next);
  }, []);

  const apply = useCallback(
    (detail: OwnPatientProfile) => {
      setLoaded({
        key: attempt,
        state: { status: "ready", detail },
        sessionId: peekSession()?.accountId ?? null,
      });
    },
    [attempt],
  );

  const isCurrentSession =
    loaded.state.status !== "ready" ||
    loaded.sessionId === (peekSession()?.accountId ?? null);

  return {
    state:
      loaded.key === attempt && isCurrentSession
        ? loaded.state
        : ({ status: "loading" } as const),
    reload,
    apply,
  };
}
