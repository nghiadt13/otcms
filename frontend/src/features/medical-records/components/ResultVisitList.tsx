import { ArrowUpRight, ChevronRight, FileCheck } from "lucide-react";
import Link from "next/link";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { VISIT_STATUS_TONE } from "@/lib/domain/clinicVisit";
import { formatDate } from "@/lib/format/formatDate";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import type { ResultVisitSummary } from "../types/examinationResult";
import {
  RESULT_STATUS_LABELS,
  groupByMonth,
  visitDateParts,
} from "./resultVisitPresentation";

/**
 * Danh sách lượt khám nhóm theo tháng. Mỗi thẻ ghi ngày, dịch vụ, "của ai" (tên + quan hệ) và trạng
 * thái bằng chữ; liên kết duy nhất của thẻ nằm ở chân thẻ (link phủ cả thẻ để bấm ở đâu cũng vào).
 */
export function ResultVisitList({ items }: { items: ResultVisitSummary[] }) {
  const groups = groupByMonth(items);
  return (
    <div className="space-y-6">
      {groups.map((group, index) => (
        <section aria-label={group.label} key={group.key}>
          <div className="mb-3 flex items-baseline justify-between gap-3">
            <h3 className="text-base font-semibold">{group.label}</h3>
            <span className="text-sm text-muted-foreground">
              {index === 0
                ? "Mới nhất trước"
                : `${group.items.length} lượt khám`}
            </span>
          </div>
          <ul className="space-y-4">
            {group.items.map((item) => (
              <ResultVisitCard item={item} key={item.visitId} />
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}

function ResultVisitCard({ item }: { item: ResultVisitSummary }) {
  const date = visitDateParts(item.visitDate);
  const done = item.status === "COMPLETED";
  return (
    <li>
      <article className="relative flex min-w-0 flex-col gap-4 rounded-xl border bg-card p-4 transition-colors focus-within:ring-2 focus-within:ring-ring hover:bg-muted/30 motion-reduce:transition-none sm:p-6">
        <div className="grid min-w-0 grid-cols-[4rem_minmax(0,1fr)] items-start gap-3 sm:grid-cols-[4rem_minmax(0,1fr)_auto] sm:gap-4">
          <div
            aria-hidden
            className={cn(
              "flex w-16 shrink-0 flex-col items-center rounded-lg px-2 py-2 text-center",
              done ? "bg-primary/10 text-primary" : "bg-muted text-foreground",
            )}
          >
            <span className="text-[0.6875rem] font-semibold tracking-wide uppercase opacity-80">
              Tháng {date.month}
            </span>
            <span className="text-2xl leading-tight font-semibold tabular-nums">
              {date.day}
            </span>
            <span className="text-xs opacity-80">{date.weekday}</span>
          </div>
          <div className="min-w-0">
            <h4 className="text-lg font-semibold break-words">
              {item.serviceName}
            </h4>
            <p className="mt-1 text-sm break-words text-muted-foreground">
              {item.patient.fullName} ·{" "}
              {CARE_RELATIONSHIP_LABELS[item.patient.relationship]}
            </p>
            <p className="text-sm text-muted-foreground tabular-nums">
              Lượt khám ngày {formatDate(item.visitDate)}
            </p>
          </div>
          <StatusBadge
            className="col-start-2 h-6 w-fit max-w-full text-sm sm:col-start-auto"
            tone={VISIT_STATUS_TONE[item.status]}
          >
            {RESULT_STATUS_LABELS[item.status]}
          </StatusBadge>
        </div>

        <div className="flex flex-col items-start gap-3 border-t pt-4 text-sm sm:flex-row sm:items-center sm:justify-between">
          <p className="flex min-w-0 items-start gap-2 text-muted-foreground sm:items-center">
            {done ? (
              <FileCheck aria-hidden className="size-4 shrink-0 text-success" />
            ) : null}
            {done ? "Kết luận đã được công bố" : "Kết quả chưa được hoàn tất"}
          </p>
          <Link
            className="inline-flex min-h-11 items-center gap-1 font-medium text-primary after:absolute after:inset-0 after:rounded-xl focus-visible:outline-none"
            href={PATIENT_ROUTES.resultDetail(item.visitId)}
          >
            {done ? "Xem kết quả" : "Chi tiết"}
            <span className="sr-only">
              {` ${item.serviceName}, ${item.patient.fullName}, ngày ${formatDate(item.visitDate)}`}
            </span>
            {done ? (
              <ArrowUpRight aria-hidden className="size-4" />
            ) : (
              <ChevronRight aria-hidden className="size-4" />
            )}
          </Link>
        </div>
      </article>
    </li>
  );
}
