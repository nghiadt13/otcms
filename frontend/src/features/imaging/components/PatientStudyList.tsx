import { ChevronRight } from "lucide-react";
import Link from "next/link";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { VISIT_STATUS_LABELS, VISIT_STATUS_TONE } from "@/lib/domain/clinicVisit";
import { formatDate } from "@/lib/format/formatDate";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import {
  IMAGING_MODALITY_LABELS,
  type PatientImagingStudySummary,
} from "../types/imagingStudy";

/**
 * Danh sách ảnh chụp: ngày, dịch vụ, loại ảnh và người bệnh (kèm quan hệ), trạng thái lượt
 * bằng chữ. Cả dòng là link tới chi tiết study.
 */
export function PatientStudyList({ items }: { items: PatientImagingStudySummary[] }) {
  return (
    <ul className="divide-y rounded-lg border bg-card">
      {items.map((item) => (
        <li key={item.id}>
          <Link
            href={PATIENT_ROUTES.imageStudy(item.id)}
            className="flex min-h-14 items-center gap-4 px-4 py-4 transition-colors hover:bg-muted/50 focus-visible:bg-muted/50 focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset motion-reduce:transition-none"
          >
            <span className="min-w-0 flex-1 space-y-1">
              <span className="block text-base font-semibold tabular-nums">
                {formatDate(item.studyDate)} · {item.serviceName}
              </span>
              <span className="block text-base text-muted-foreground">
                {IMAGING_MODALITY_LABELS[item.modality]} · {item.patient.fullName} (
                {CARE_RELATIONSHIP_LABELS[item.patient.relationship]})
              </span>
            </span>
            <StatusBadge
              tone={VISIT_STATUS_TONE[item.visitStatus]}
              className="h-6 shrink-0 text-sm"
            >
              {VISIT_STATUS_LABELS[item.visitStatus]}
            </StatusBadge>
            <ChevronRight aria-hidden className="size-5 shrink-0 text-muted-foreground" />
          </Link>
        </li>
      ))}
    </ul>
  );
}
