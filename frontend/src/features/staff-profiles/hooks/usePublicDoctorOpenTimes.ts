"use client";

import { useCallback, useEffect, useState } from "react";
import { listPublicDoctorOpenTimes } from "../api/publicDoctorOpenTimesApi";
import type { DoctorOpenTime } from "../types/publicDoctor";

type LoadState =
  | { status: "loading" }
  | { status: "failed" }
  | { status: "ready"; openTimes: Map<string, DoctorOpenTime[]> };

export interface PublicDoctorOpenTimesState {
  /** Free times by doctor id; null while loading or after a failure. */
  openTimes: Map<string, DoctorOpenTime[]> | null;
  loading: boolean;
  failed: boolean;
  reload: () => void;
}

/** Free times of all public doctors, loaded once when the page opens. `reload` retries after a failure. */
export function usePublicDoctorOpenTimes(): PublicDoctorOpenTimesState {
  const [state, setState] = useState<LoadState>({ status: "loading" });
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    listPublicDoctorOpenTimes(controller.signal)
      .then((openTimes) => {
        if (!controller.signal.aborted) setState({ status: "ready", openTimes });
      })
      .catch(() => {
        if (!controller.signal.aborted) setState({ status: "failed" });
      });
    return () => controller.abort();
  }, [reloadToken]);

  const reload = useCallback(() => {
    setState({ status: "loading" });
    setReloadToken((n) => n + 1);
  }, []);

  return {
    openTimes: state.status === "ready" ? state.openTimes : null,
    loading: state.status === "loading",
    failed: state.status === "failed",
    reload,
  };
}
