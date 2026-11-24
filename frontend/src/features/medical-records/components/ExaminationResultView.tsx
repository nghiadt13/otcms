import { ArrowLeft, CalendarDays, CircleAlert, UserRound } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import {
  VISIT_STATUS_LABELS,
  VISIT_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
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
export function ExaminationResultView({
  actions,
  result,
}: {
  actions?: ReactNode;
  result: ExaminationResult;
}) {
  const sections = buildResultSections(result);
  const incomplete = result.status === "INCOMPLETE";
  return (
    <div className="min-w-0 space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <Link
          href={PATIENT_ROUTES.results}
          className="inline-flex min-h-11 items-center gap-2 rounded-md text-base font-semibold text-primary underline-offset-4 hover:underline focus-visible:ring-2 focus-visible:ring-ring"
        >
          <ArrowLeft aria-hidden className="size-5" />
          Kết quả khám
        </Link>
        <VisitNavigation
          previousVisitId={result.previousVisitId}
          nextVisitId={result.nextVisitId}
        />
      </div>

      <header className="rounded-[12px] border bg-card px-4 pt-4 text-card-foreground sm:px-5">
        <PortalPageHeader
          actions={actions}
          title="Chi tiết kết quả khám"
          description={
            <span className="flex flex-wrap items-center gap-x-4 gap-y-2">
              <span className="inline-flex min-w-0 items-start gap-2">
                <UserRound
                  aria-hidden
                  className="mt-0.5 size-5 shrink-0 text-primary"
                />
                <span className="wrap-break-word">
                  <span className="font-semibold text-foreground">
                    {result.patient.fullName}
                  </span>
                  {" ("}
                  {CARE_RELATIONSHIP_LABELS[result.patient.relationship]}
                  {")"}
                </span>
              </span>
              <span className="inline-flex items-center gap-2">
                <CalendarDays
                  aria-hidden
                  className="size-5 shrink-0 text-primary"
                />
                <time dateTime={result.visitDate} className="tabular-nums">
                  {formatDate(result.visitDate)}
                </time>
              </span>
              <StatusBadge
                tone={VISIT_STATUS_TONE[result.status]}
                className="h-auto px-2.5 py-1 text-sm"
              >
                {VISIT_STATUS_LABELS[result.status]}
              </StatusBadge>
            </span>
          }
        />
      </header>

      {incomplete ? (
        <Alert
          role="status"
          className="rounded-[12px] border-warning/30 bg-warning-soft px-4 py-3 text-warning"
        >
          <CircleAlert aria-hidden />
          <AlertTitle className="text-base">
            Lượt khám chưa được bác sĩ kết luận.
          </AlertTitle>
          <AlertDescription className="text-base">
            Các mục kết luận và đơn thuốc sẽ hiển thị khi hồ sơ được hoàn tất.
            Thông tin hành chính, hình ảnh và PDF đã phát hành vẫn có thể xem.
          </AlertDescription>
        </Alert>
      ) : null}

      {sections.length > 0 ? (
        <nav aria-label="Mục lục kết quả" className="flex flex-wrap gap-2">
          {sections.map((section) => (
            <a
              key={section.id}
              href={`#${section.id}`}
              className="inline-flex min-h-11 items-center gap-2 rounded-full border border-primary/15 bg-secondary px-3 text-base font-medium text-primary hover:border-primary/40 hover:bg-accent focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 motion-reduce:transition-none"
            >
              <section.icon aria-hidden className="size-4 shrink-0" />
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
