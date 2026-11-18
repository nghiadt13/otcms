"use client";

import { useState } from "react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { fetchBlob } from "@/lib/api/client";
import { formatDateTime } from "@/lib/format/formatDate";
import { regenerateResultSheet } from "../api/doctorExamLiveResultSheets";
import { useAsyncAction } from "../hooks/useAsyncAction";
import { useResultSheets } from "../hooks/useDoctorData";
import { notifyDoctorError } from "../schema/doctorErrors";
import { AmendResultDialog } from "./AmendResultDialog";
import { DoctorLoadState } from "./DoctorLoadState";

export function ResultSheetPanel({ visitId }: { visitId: string }) {
  const sheets = useResultSheets(visitId);
  const { pending, run } = useAsyncAction();
  const [opening, setOpening] = useState(false);
  const [amending, setAmending] = useState(false);

  async function openPdf(documentId: string) {
    const viewer = window.open("about:blank", "_blank");
    if (!viewer) {
      notifyDoctorError(new Error());
      return;
    }
    viewer.opener = null;
    setOpening(true);
    try {
      const { blob } = await fetchBlob(`/api/v1/medical-documents/${encodeURIComponent(documentId)}/content`);
      const url = URL.createObjectURL(blob);
      viewer.location.href = url;
      window.setTimeout(() => URL.revokeObjectURL(url), 5 * 60 * 1000);
    } catch (error) {
      viewer.close();
      notifyDoctorError(error);
    } finally {
      setOpening(false);
    }
  }

  async function regenerate() {
    const done = await run(async () => { await regenerateResultSheet(visitId); return true; });
    if (done) sheets.refresh();
  }

  return (
    <section aria-labelledby="sheet-title" className="space-y-3 rounded-lg border bg-card p-5">
      <h2 id="sheet-title" className="text-lg font-semibold">Phiếu kết quả khám</h2>
      {sheets.loading ? (
        <Skeleton className="h-16 w-full" />
      ) : sheets.error ? (
        <DoctorLoadState loading={false} error={sheets.error} retry={sheets.refresh} />
      ) : !sheets.data || sheets.data.length === 0 ? (
        <div className="space-y-3">
          <p className="text-sm text-muted-foreground">Chưa có phiếu kết quả.</p>
          <Button type="button" variant="outline" disabled={pending} onClick={() => void regenerate()}>
            {pending ? "Đang sinh phiếu..." : "Sinh lại phiếu"}
          </Button>
        </div>
      ) : (
        <ul className="divide-y">
          {sheets.data.map((sheet) => (
            <li key={sheet.documentId ?? sheet.version} className="flex flex-wrap items-center justify-between gap-3 py-2.5">
              <div>
                <p className="text-sm font-medium">Phiên bản {sheet.version}</p>
                {sheet.issuedAt ? <p className="text-xs text-muted-foreground">{formatDateTime(sheet.issuedAt)}</p> : null}
              </div>
              <div className="flex flex-wrap items-center gap-3">
                <StatusBadge tone={sheet.status === "CURRENT" ? "success" : "neutral"}>
                  {sheet.status === "CURRENT" ? "Hiện hành" : "Đã thay thế"}
                </StatusBadge>
                {sheet.status === "CURRENT" && sheet.documentId && sheet.issuedAt ? (
                  <Button type="button" variant="outline" disabled={opening} onClick={() => void openPdf(sheet.documentId!)}>
                    {opening ? "Đang mở..." : "Xem phiếu kết quả"}
                  </Button>
                ) : null}
              </div>
            </li>
          ))}
        </ul>
      )}
      {sheets.data?.some((sheet) => sheet.status === "CURRENT") ? (
        <div className="border-t pt-3">
          <Button type="button" variant="outline" onClick={() => setAmending(true)}>
            Sửa kết quả
          </Button>
        </div>
      ) : null}
      <AmendResultDialog
        visitId={visitId}
        open={amending}
        onClose={() => setAmending(false)}
        onAmended={sheets.refresh}
      />
    </section>
  );
}
