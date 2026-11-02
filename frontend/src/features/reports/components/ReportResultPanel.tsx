import { Skeleton } from "@/components/ui/skeleton";
import { formatDate, formatDateTime } from "@/lib/format/formatDate";
import type {
  ReportExportFormat,
  ReportResult,
  ReportSubjectOption,
} from "../types/report";
import { BREAKDOWN_LABELS } from "../types/reportLabels";
import { ReportExportMenu } from "./ReportExportMenu";
import { ReportResultTable } from "./ReportResultTable";

type ReportResultPanelProps = {
  result: ReportResult | null;
  running: boolean;
  exporting: boolean;
  subjects: ReportSubjectOption[];
  onExport: (format: ReportExportFormat) => void;
};

/** Khối kết quả: trạng thái ban đầu, đang tạo, rỗng hoặc 1–3 bảng kèm nút Xuất. */
export function ReportResultPanel({
  result,
  running,
  exporting,
  subjects,
  onExport,
}: ReportResultPanelProps) {
  if (!result && !running) {
    return (
      <p className="rounded-lg border border-dashed p-6 text-sm text-muted-foreground">
        Chọn chủ đề và khoảng ngày, rồi bấm Tạo báo cáo để xem số liệu.
      </p>
    );
  }
  // Đang tạo lần đầu: skeleton. Đang tạo lại: giữ kết quả cũ, đánh dấu đang bận.
  if (!result) {
    return (
      <section
        aria-busy="true"
        aria-label="Đang tạo báo cáo"
        className="grid gap-3"
      >
        <Skeleton className="h-6 w-1/2" />
        <Skeleton className="h-10 w-full" />
        <Skeleton className="h-10 w-full" />
        <Skeleton className="h-10 w-full" />
      </section>
    );
  }

  const { query } = result;
  const subjectLabel =
    subjects.find((s) => s.code === query.subject)?.label ?? "Báo cáo";
  const title = [
    subjectLabel,
    query.breakdown
      ? `Theo ${BREAKDOWN_LABELS[query.breakdown].toLowerCase()}`
      : null,
    `${formatDate(query.from)} – ${formatDate(query.to)}`,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <section
      aria-labelledby="report-result-title"
      aria-busy={running}
      className={
        running
          ? "grid gap-4 opacity-60 transition-opacity motion-reduce:transition-none"
          : "grid gap-4"
      }
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="report-result-title" className="text-base font-semibold">
            {title}
          </h2>
          <p className="mt-0.5 text-sm text-muted-foreground tabular-nums">
            Tạo lúc {formatDateTime(result.generatedAt)}
          </p>
        </div>
        {result.allowedActions.includes("export") ? (
          <ReportExportMenu exporting={exporting} onExport={onExport} />
        ) : null}
      </div>
      {result.hasData ? (
        result.sections.map((section) => (
          <ReportResultTable key={section.title} section={section} />
        ))
      ) : (
        <div className="rounded-lg border border-dashed p-6">
          <p className="text-sm font-medium">Không có hoạt động trong kỳ này</p>
          <p className="mt-1 text-sm text-muted-foreground">
            Chọn khoảng ngày hoặc chủ đề khác để xem số liệu.
          </p>
        </div>
      )}
    </section>
  );
}
