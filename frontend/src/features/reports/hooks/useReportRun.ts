import { useCallback, useEffect, useRef, useState } from "react";
import { isApiError, toFieldErrorMap, type MsgCode } from "@/lib/api/apiError";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { createReport, exportReport } from "../api/reportApi";
import type {
  ReportExportFormat,
  ReportQuery,
  ReportResult,
} from "../types/report";

// Câu MSG-73 theo spec 13 (bảng appMessages đang là bản nháp ngắn hơn).
const REPORT_READY_TEXT = "Báo cáo đã sẵn sàng để xem hoặc xuất.";

export interface GenerateOutcome {
  /** Lỗi theo ô khi BE trả 400 kèm fieldErrors. */
  fieldErrors?: Record<string, MsgCode>;
  /** 403 MSG-50: màn nên tải lại danh sách chủ đề. */
  forbidden?: boolean;
}

/** Tạo báo cáo và xuất tệp. Giữ một AbortController; chỉ phản hồi của request mới nhất được dùng. */
export function useReportRun() {
  const [result, setResult] = useState<ReportResult | null>(null);
  const [running, setRunning] = useState(false);
  const [exporting, setExporting] = useState(false);
  const runCtrl = useRef<AbortController | null>(null);
  const exportCtrl = useRef<AbortController | null>(null);

  useEffect(
    () => () => {
      runCtrl.current?.abort();
      exportCtrl.current?.abort();
    },
    [],
  );

  const generate = useCallback(
    async (query: ReportQuery): Promise<GenerateOutcome> => {
      runCtrl.current?.abort();
      const ctrl = new AbortController();
      runCtrl.current = ctrl;
      setRunning(true);
      try {
        const next = await createReport(query, ctrl.signal);
        if (ctrl.signal.aborted) return {};
        setResult(next);
        notifySuccess(REPORT_READY_TEXT);
        return {};
      } catch (err) {
        if (ctrl.signal.aborted) return {};
        // Kết quả cũ giữ nguyên; lỗi theo ô trả về cho màn, còn lại là toast.
        if (isApiError(err) && err.fieldErrors?.length)
          return { fieldErrors: toFieldErrorMap(err.fieldErrors) };
        notifyError(err);
        return { forbidden: isApiError(err) && err.status === 403 };
      } finally {
        if (!ctrl.signal.aborted) setRunning(false);
      }
    },
    [],
  );

  const reset = useCallback(() => {
    runCtrl.current?.abort();
    exportCtrl.current?.abort();
    setResult(null);
    setRunning(false);
    setExporting(false);
  }, []);

  /** Xuất theo `result.query` (kỳ đã tạo), không theo form hiện tại. */
  const exportFile = useCallback(
    async (format: ReportExportFormat) => {
      if (!result) return;
      exportCtrl.current?.abort();
      const ctrl = new AbortController();
      exportCtrl.current = ctrl;
      setExporting(true);
      try {
        await exportReport(result.query, format, ctrl.signal);
      } catch (err) {
        if (!ctrl.signal.aborted) notifyError(err);
      } finally {
        if (!ctrl.signal.aborted) setExporting(false);
      }
    },
    [result],
  );

  return { result, running, exporting, generate, reset, exportFile };
}
