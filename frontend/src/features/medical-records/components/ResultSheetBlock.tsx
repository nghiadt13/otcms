"use client";

import { ExternalLink } from "lucide-react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { formatDate } from "@/lib/format/formatDate";
import { fetchBlob } from "@/lib/api/client";
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
    (a, b) =>
      b.issuedAt.localeCompare(a.issuedAt) || b.version - a.version,
  );
  return (
    <ul className="divide-y rounded-lg border">
      {ordered.map((sheet) => {
        const current = sheet.status === "CURRENT";
        return (
          <li
            key={sheet.id ?? sheet.version}
            className="flex flex-wrap items-center justify-between gap-3 px-4 py-3"
          >
            <div className="min-w-0 space-y-1">
              <p className="flex flex-wrap items-center gap-2 text-base font-medium">
                {sheet.title || `Phiên bản ${sheet.version}`}
                {sheet.title ? (
                  <span className="text-sm font-normal text-muted-foreground">
                    Phiên bản {sheet.version}
                  </span>
                ) : null}
                <StatusBadge
                  tone={current ? "success" : "neutral"}
                  className="h-6 text-sm"
                >
                  {current ? "Hiện hành" : "Đã thay thế"}
                </StatusBadge>
              </p>
              <p className="text-base text-muted-foreground tabular-nums">
                Phát hành {formatDate(sheet.issuedAt)}
              </p>
            </div>
            <ResultSheetButton
              sheet={sheet}
              className={publicButtonClass("secondary")}
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
    <div className="flex flex-col items-end gap-1">
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
        {loading ? "Đang mở…" : "Xem PDF"}
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
