"use client";

import { ExternalLink } from "lucide-react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { formatDate } from "@/lib/format/formatDate";
import { fetchBlob } from "@/lib/api/client";
import { cn } from "@/lib/utils";
import type { ResultSheetVersion } from "../types/examinationResult";
import { useState } from "react";

/**
 * Phiếu PDF đã phát hành; tải nội dung qua API có xác thực trước khi mở tab xem.
 */
export function ResultSheetBlock({ sheets }: { sheets: ResultSheetVersion[] }) {
  if (sheets.length === 0)
    return (
      <p className="text-base text-muted-foreground">
        Phiếu kết quả chưa được phát hành.
      </p>
    );
  const ordered = [...sheets].sort(
    (a, b) => b.issuedAt.localeCompare(a.issuedAt) || b.version - a.version,
  );
  return (
    <ul className="divide-y">
      {ordered.map((sheet) => {
        const current = sheet.status === "CURRENT";
        return (
          <li
            key={sheet.id ?? sheet.version}
            className="grid items-center gap-2 py-2.5 first:pt-0 last:pb-0 sm:grid-cols-[minmax(0,1fr)_auto] sm:gap-4"
          >
            <div className="min-w-0 space-y-0.5">
              <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-base">
                <span className="min-w-0 wrap-break-word font-semibold">
                  {sheet.title || `Phiên bản ${sheet.version}`}
                </span>
                {sheet.title ? (
                  <span className="text-sm font-normal text-muted-foreground">
                    Phiên bản {sheet.version}
                  </span>
                ) : null}
                <StatusBadge
                  tone={current ? "success" : "neutral"}
                  className="h-auto py-0.5 text-sm"
                >
                  {current ? "Hiện hành" : "Đã thay thế"}
                </StatusBadge>
              </p>
              <p className="text-base text-muted-foreground tabular-nums">
                Phát hành{" "}
                <time dateTime={sheet.issuedAt}>
                  {formatDate(sheet.issuedAt)}
                </time>
              </p>
            </div>
            <ResultSheetButton
              sheet={sheet}
              className={cn(publicButtonClass("secondary"), "h-11 px-3")}
            />
          </li>
        );
      })}
    </ul>
  );
}

function ResultSheetButton({
  sheet,
  className,
}: {
  sheet: ResultSheetVersion;
  className: string;
}) {
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const openPdf = async () => {
    setError(null);
    const viewer = window.open("about:blank", "_blank");
    if (!viewer) {
      setError("Trình duyệt đã chặn cửa sổ xem PDF.");
      return;
    }
    setLoading(true);
    try {
      const { blob } = await fetchBlob(sheet.fileUrl as `/api/v1/${string}`);
      const url = URL.createObjectURL(blob);
      viewer.opener = null;
      viewer.location.href = url;
      window.setTimeout(() => URL.revokeObjectURL(url), 5 * 60 * 1000);
    } catch {
      viewer.close();
      setError("Không thể mở phiếu kết quả. Vui lòng thử lại.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-w-0 flex-col items-start gap-1 sm:items-end">
      <a
        aria-disabled={loading}
        className={className}
        href={sheet.fileUrl}
        onClick={(event) => {
          event.preventDefault();
          if (!loading) void openPdf();
        }}
        rel="noopener noreferrer"
        target="_blank"
      >
        {loading
          ? "Đang mở…"
          : sheet.status === "CURRENT" &&
              sheet.fileUrl.startsWith("/api/v1/medical-documents/")
            ? "Xem phiếu kết quả"
            : "Xem PDF"}
        <span className="sr-only"> phiên bản {sheet.version}</span>
        {!loading ? <ExternalLink aria-hidden /> : null}
      </a>
      {error ? (
        <span className="text-sm text-destructive" role="alert">
          {error}
        </span>
      ) : null}
    </div>
  );
}
