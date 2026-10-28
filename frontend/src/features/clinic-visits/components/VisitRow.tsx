import Link from "next/link";
import { ChevronRight } from "lucide-react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import {
  VISIT_STATUS_LABELS,
  VISIT_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import type { VisitSummary } from "../types/visitProgress";
import { visitDateLabel, visitPatientLabel } from "./visitPresentation";

// Một dòng lượt khám: cả dòng là link tới chi tiết. Ghi ngày, người khám (kèm quan hệ) và trạng thái.
export function VisitRow({ visit }: { visit: VisitSummary }) {
  return (
    <li>
      <Link
        className="flex min-h-12 items-center gap-3 px-4 py-4 transition-colors hover:bg-muted/50 focus-visible:bg-muted/50 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring/50 motion-reduce:transition-none"
        href={PATIENT_ROUTES.visitDetail(visit.id)}
      >
        <span className="grid min-w-0 flex-1 gap-1 text-base">
          <span className="font-semibold tabular-nums">
            {visitDateLabel(visit.visitDate)}
          </span>
          <span className="break-words">
            {visitPatientLabel(visit.patient)}
          </span>
          <span className="mt-1">
            <StatusBadge tone={VISIT_STATUS_TONE[visit.status]}>
              {VISIT_STATUS_LABELS[visit.status]}
            </StatusBadge>
          </span>
        </span>
        <ChevronRight
          aria-hidden
          className="size-5 shrink-0 text-muted-foreground"
        />
      </Link>
    </li>
  );
}
