"use client";

import { useEffect, useState } from "react";

/** Discard stale results when filters change or the component is unmounted. */
export function useAdminRemoteData<T>(
  load: (signal: AbortSignal) => Promise<T>,
) {
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState<{
    load: typeof load;
    revision: number;
    data?: T;
    error?: unknown;
  }>();
  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) setResult({ load, revision, data });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ load, revision, error });
      },
    );
    return () => controller.abort();
  }, [load, revision]);
  const current =
    result?.load === load && result.revision === revision ? result : undefined;
  return {
    data: current?.data,
    error: current?.error,
    loading: !current,
    refresh: () => setRevision((v) => v + 1),
  };
}
