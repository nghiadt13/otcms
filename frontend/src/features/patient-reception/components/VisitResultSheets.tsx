"use client";

import { useCallback, useState } from "react";
import { Button } from "@/components/ui/button";
import { useAdminRemoteData } from "@/hooks/useAdminRemoteData";
import { apiRequest, fetchBlob } from "@/lib/api/client";
import { messageFromError, formatAppMessage } from "@/lib/messages/appMessages";

type Sheet = { documentId: string; version: number; status: "ACTIVE" | "REPLACED"; releasedAt: string | null };

export function VisitResultSheets({ visitId }: { visitId: string }) {
  const sheets = useAdminRemoteData(useCallback(async (signal: AbortSignal) => {
    const response = await apiRequest<{ items: Sheet[] }>(`/api/v1/visits/${encodeURIComponent(visitId)}/result-sheets`, { signal });
    return response.items;
  }, [visitId]));
  const [opening, setOpening] = useState(false);
  const [error, setError] = useState("");

  async function openPdf(documentId: string) {
    setError("");
    const viewer = window.open("about:blank", "_blank");
    if (!viewer) { setError(formatAppMessage("MSG-04")); return; }
    setOpening(true);
    try {
      const { blob } = await fetchBlob(`/api/v1/medical-documents/${encodeURIComponent(documentId)}/content`);
      const url = URL.createObjectURL(blob);
      viewer.opener = null;
      viewer.location.href = url;
      window.setTimeout(() => URL.revokeObjectURL(url), 5 * 60 * 1000);
    } catch (cause) {
      viewer.close();
      setError(messageFromError(cause));
    } finally { setOpening(false); }
  }

  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold">Phiếu kết quả khám</h2>
      {sheets.loading ? <p>Đang tải phiếu kết quả…</p> : null}
      {sheets.error ? <div role="alert"><p>{messageFromError(sheets.error)}</p><Button type="button" variant="outline" onClick={sheets.refresh}>Thử lại</Button></div> : null}
      {sheets.data?.length === 0 ? <p>Chưa có phiếu kết quả.</p> : null}
      {sheets.data?.length ? <ul className="divide-y rounded-lg border">
        {[...sheets.data].sort((a, b) => b.version - a.version).map((sheet) => (
          <li key={sheet.documentId} className="flex flex-wrap items-center justify-between gap-3 p-3">
            <span>Phiên bản {sheet.version} · {sheet.status === "ACTIVE" ? "Hiện hành" : "Đã thay thế"}</span>
            {sheet.status === "ACTIVE" ? <Button type="button" variant="outline" disabled={opening} onClick={() => void openPdf(sheet.documentId)}>Xem / in PDF</Button> : null}
          </li>
        ))}
      </ul> : null}
      {error ? <p role="alert">{error}</p> : null}
    </section>
  );
}
