import { ExternalLink } from "lucide-react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { formatDate } from "@/lib/format/formatDate";
import type { ResultSheetVersion } from "../types/examinationResult";

/**
 * Phiếu kết quả PDF theo phiên bản, bản mới nhất đứng đầu. Bản cũ ghi "Đã thay thế"
 * bằng chữ. `fileUrl` mở tab mới với `noopener`; FE không lưu URL.
 */
export function ResultSheetBlock({ sheets }: { sheets: ResultSheetVersion[] }) {
  if (sheets.length === 0)
    return (
      <p className="text-base text-muted-foreground">
        Phiếu kết quả chưa được phát hành.
      </p>
    );
  const ordered = [...sheets].sort((a, b) => b.version - a.version);
  return (
    <ul className="divide-y rounded-lg border">
      {ordered.map((sheet) => {
        const current = sheet.status === "CURRENT";
        return (
          <li
            key={sheet.version}
            className="flex flex-wrap items-center justify-between gap-3 px-4 py-3"
          >
            <div className="min-w-0 space-y-1">
              <p className="flex flex-wrap items-center gap-2 text-base font-medium">
                Phiên bản {sheet.version}
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
            <a
              href={sheet.fileUrl}
              target="_blank"
              rel="noopener noreferrer"
              className={publicButtonClass("secondary")}
            >
              Xem PDF
              <span className="sr-only"> phiên bản {sheet.version}</span>
              <ExternalLink aria-hidden />
            </a>
          </li>
        );
      })}
    </ul>
  );
}
