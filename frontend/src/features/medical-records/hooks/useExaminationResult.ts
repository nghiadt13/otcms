"use client";

import { useCallback, useEffect, useState } from "react";
import { getVisitResult } from "../api/examinationResultApi";
import { listPatientResultSheets } from "../api/resultSheetApi";
import type {
  ExaminationResult,
  ResultSheetVersion,
} from "../types/examinationResult";

/** Kết quả lượt khám và danh sách phiếu đã phát hành theo quyền bệnh nhân. */
export function useExaminationResult(visitId: string) {
  const [result, setResult] = useState<ExaminationResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [sheetError, setSheetError] = useState<unknown>(null);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    // eslint-disable-next-line react-hooks/set-state-in-effect -- bắt đầu tải là hệ quả của đổi lượt khám hoặc bấm thử lại
    setLoading(true);
    setError(null);
    setSheetError(null);
    getVisitResult(visitId, controller.signal)
      .then(async (data) => {
        if (data.status !== "COMPLETED") return data;
        let versions;
        try {
          versions = await listPatientResultSheets(visitId, controller.signal);
        } catch (err: unknown) {
          if (!controller.signal.aborted) setSheetError(err);
          return data;
        }
        const sheets: ResultSheetVersion[] = versions.map((version) => ({
          id: version.documentId,
          version: version.version,
          status: version.status === "ACTIVE" ? "CURRENT" : "REPLACED",
          issuedAt: version.releasedAt,
          title: "Phiếu kết quả khám",
          fileUrl: `/api/v1/medical-documents/${encodeURIComponent(version.documentId)}/content`,
        }));
        const otherSheets = (data.resultSheets ?? []).filter(
          (sheet) => !sheet.fileUrl.startsWith("/api/v1/medical-documents/"),
        );
        const resultSheets = [...sheets, ...otherSheets];
        return {
          ...data,
          resultSheets: resultSheets.length ? resultSheets : undefined,
          visibleBlocks:
            resultSheets.length && !data.visibleBlocks.includes("RESULT_SHEET")
              ? [...data.visibleBlocks, "RESULT_SHEET" as const]
              : data.visibleBlocks,
        };
      })
      .then((data) => {
        if (controller.signal.aborted) return;
        setResult(data);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setError(err);
        setLoading(false);
      });
    return () => controller.abort();
  }, [visitId, reloadToken]);

  const reload = useCallback(() => setReloadToken((n) => n + 1), []);

  return { result, loading, error, sheetError, reload };
}
