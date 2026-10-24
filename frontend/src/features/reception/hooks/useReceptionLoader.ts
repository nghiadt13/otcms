import { useCallback, useEffect, useRef, useState } from "react";

interface LoaderResult<T> {
  scope: string;
  request: string;
  data?: T;
  error?: unknown;
}

/**
 * Tải dữ liệu theo `scope` (đổi bệnh nhân hay bộ lọc thì bỏ dữ liệu của phạm vi cũ) và
 * `reload()` tải lại cùng phạm vi, giữ dữ liệu cũ trên màn trong lúc chờ. Request đang bay
 * bị hủy khi đổi phạm vi hoặc khi unmount, nên kết quả cũ không ghi đè kết quả mới.
 */
export function useReceptionLoader<T>(scope: string, load: (signal: AbortSignal) => Promise<T>) {
  const [version, setVersion] = useState(0);
  const [result, setResult] = useState<LoaderResult<T> | null>(null);
  const loadRef = useRef(load);
  useEffect(() => {
    loadRef.current = load;
  });

  const request = `${scope}#${version}`;
  useEffect(() => {
    const controller = new AbortController();
    loadRef.current(controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) setResult({ scope, request, data });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ scope, request, error });
      },
    );
    return () => controller.abort();
  }, [scope, request]);

  const reload = useCallback(() => setVersion((value) => value + 1), []);
  const current = result?.request === request;
  return {
    data: result && result.scope === scope ? result.data : undefined,
    error: result && current ? result.error : undefined,
    loading: !current,
    reload,
  };
}
