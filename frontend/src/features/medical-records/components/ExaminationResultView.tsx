import { ChevronLeft } from "lucide-react";
import Link from "next/link";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { VISIT_STATUS_LABELS, VISIT_STATUS_TONE } from "@/lib/domain/clinicVisit";
import { formatDate } from "@/lib/format/formatDate";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import type { ExaminationResult } from "../types/examinationResult";
import {
  ExaminationResultBlocks,
  buildResultSections,
} from "./ExaminationResultBlocks";
import { VisitNavigation } from "./VisitNavigation";

/**
 * Chi tiết một lượt khám. Đầu trang nói rõ "của ai" và trạng thái; lượt Chưa hoàn tất
 * có thông báo; mục lục chỉ neo tới các khối thật sự hiện; các khối theo `visibleBlocks`.
 */
export function ExaminationResultView({ result }: { result: ExaminationResult }) {
  const sections = buildResultSections(result);
  const incomplete = result.status === "INCOMPLETE";
  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Link
          href={PATIENT_ROUTES.results}
          className="inline-flex min-h-11 items-center gap-1 rounded-md text-base font-medium text-primary underline-offset-4 hover:underline focus-visible:ring-2 focus-visible:ring-ring motion-reduce:transition-none"
        >
          <ChevronLeft aria-hidden />
          Kết quả khám
        </Link>
        <VisitNavigation
          previousVisitId={result.previousVisitId}
          nextVisitId={result.nextVisitId}
        />
      </div>

      <PortalPageHeader
        title={`Kết quả khám ngày ${formatDate(result.visitDate)}`}
        description={
          <>
            <span>
              {result.patient.fullName} (
              {CARE_RELATIONSHIP_LABELS[result.patient.relationship]})
            </span>{" "}
            <StatusBadge
              tone={VISIT_STATUS_TONE[result.status]}
              className="h-6 align-middle text-sm"
            >
              {VISIT_STATUS_LABELS[result.status]}
            </StatusBadge>
          </>
        }
      />

      {incomplete ? (
        <Alert role="status" className="border-warning/40 bg-warning-soft">
          <AlertTitle className="text-base">
            Lượt khám chưa được bác sĩ kết luận.
          </AlertTitle>
          <AlertDescription className="text-base">
            Chỉ hiện ảnh và kết quả xét nghiệm.
          </AlertDescription>
        </Alert>
      ) : null}

      {sections.length > 0 ? (
        <nav
          aria-label="Mục lục kết quả"
          className="flex flex-wrap gap-x-5 gap-y-1 border-y py-2"
        >
          {sections.map((section) => (
            <a
              key={section.id}
              href={`#${section.id}`}
              className="inline-flex min-h-11 items-center rounded-md text-base font-medium text-primary underline-offset-4 hover:underline focus-visible:ring-2 focus-visible:ring-ring"
            >
              {section.shortTitle}
            </a>
          ))}
        </nav>
      ) : (
        <p className="text-base text-muted-foreground">
          Lượt khám này chưa có nội dung để hiển thị.
        </p>
      )}

      <ExaminationResultBlocks sections={sections} />
    </div>
  );
}
