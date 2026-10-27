import { useCallback, useEffect, useRef, useState } from "react";
import { isApiError } from "@/lib/api/apiError";
import { messageFromError } from "@/lib/messages/appMessages";
import { listReportSubjects } from "../api/reportApi";
import type { ReportSubjectOption } from "../types/report";

/** Tải chủ đề trong quyền. Lỗi → `error` là câu hiển thị + `reload` để thử lại. */
export function useReportSubjects() {
  const [subjects, setSubjects] = useState<ReportSubjectOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const controller = useRef<AbortController | null>(null);

  const reload = useCallback(async () => {
    controller.current?.abort();
    const ctrl = new AbortController();
    controller.current = ctrl;
    setLoading(true);
    setError(null);
    try {
      const items = await listReportSubjects(ctrl.signal);
      if (ctrl.signal.aborted) return;
      setSubjects(items);
    } catch (err) {
      if (ctrl.signal.aborted) return;
      setSubjects([]);
      setError(messageFromError(isApiError(err) ? err : undefined));
    } finally {
      if (!ctrl.signal.aborted) setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- tải dữ liệu khi mở màn
    void reload();
    return () => controller.current?.abort();
  }, [reload]);

  return { subjects, loading, error, reload };
}
