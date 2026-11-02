import { ChevronRight } from "lucide-react";
import Link from "next/link";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import {
  VISIT_STATUS_LABELS,
  VISIT_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
import { formatDate } from "@/lib/format/formatDate";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import type { ResultVisitSummary } from "../types/examinationResult";

/**
 * Danh sách lượt khám: cả dòng là link tới chi tiết. Mỗi dòng nói rõ ngày, dịch vụ,
 * "của ai" (tên + quan hệ) và trạng thái bằng chữ.
 */
export function ResultVisitList({ items }: { items: ResultVisitSummary[] }) {
  return (
    <ul className="divide-y rounded-lg border bg-card">
      {items.map((item) => (
        <li key={item.visitId}>
          <Link
            href={PATIENT_ROUTES.resultDetail(item.visitId)}
            className="flex min-h-14 items-center gap-3 px-4 py-4 transition-colors hover:bg-muted/50 focus-visible:bg-muted/50 focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset motion-reduce:transition-none sm:gap-4"
          >
            {/* Dưới sm: dòng 1 là tiêu đề, dòng 2 là "tên (quan hệ) · trạng thái" (không bị ép thành 3 dòng
                ở 375px). Từ sm: trạng thái nằm bên phải, tên nằm dưới tiêu đề. Chỉ có một nhãn trạng thái. */}
            <span className="grid min-w-0 flex-1 gap-1 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center sm:gap-x-4">
              <span className="block min-w-0 text-base font-semibold tabular-nums sm:col-start-1 sm:row-start-1">
                {formatDate(item.visitDate)} · {item.serviceName}
              </span>
              <span className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1 text-base text-muted-foreground sm:contents">
                <span className="min-w-0 sm:col-start-1 sm:row-start-2">
                  {item.patient.fullName} (
                  {CARE_RELATIONSHIP_LABELS[item.patient.relationship]})
                </span>
                <span aria-hidden="true" className="sm:hidden">
                  ·
                </span>
                <StatusBadge
                  tone={VISIT_STATUS_TONE[item.status]}
                  className="h-6 shrink-0 text-sm sm:col-start-2 sm:row-span-2 sm:row-start-1 sm:justify-self-end"
                >
                  {VISIT_STATUS_LABELS[item.status]}
                </StatusBadge>
              </span>
            </span>
            <ChevronRight
              aria-hidden
              className="size-5 shrink-0 text-muted-foreground"
            />
          </Link>
        </li>
      ))}
    </ul>
  );
}
