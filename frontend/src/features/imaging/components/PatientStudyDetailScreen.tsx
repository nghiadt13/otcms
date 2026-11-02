"use client";

import { ChevronLeft } from "lucide-react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { isApiError } from "@/lib/api/apiError";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { formatDate } from "@/lib/format/formatDate";
import { messageFromError } from "@/lib/messages/appMessages";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { usePatientStudy } from "../hooks/usePatientStudy";
import { PatientStudyDetail } from "./PatientStudyDetail";

/**
 * Màn một study. 403/404 đi tới trang "không tìm thấy"; lỗi khác hiện thông báo kèm
 * nút thử lại.
 */
export function PatientStudyDetailScreen({ studyId }: { studyId: string }) {
  const { study, loading, error, reload } = usePatientStudy(studyId);
  const code = isApiError(error) ? error.code : undefined;
  if (code === "MSG-40" || code === "MSG-41") notFound();

  if (error)
    return (
      <div
        role="alert"
        className="space-y-4 rounded-lg border bg-card px-6 py-8 text-center"
      >
        <p className="text-base text-destructive">{messageFromError(error)}</p>
        <Button
          variant="outline"
          className="h-12 px-5 text-base"
          onClick={reload}
        >
          Thử lại
        </Button>
      </div>
    );

  if (loading || !study)
    return (
      <div aria-hidden className="space-y-6">
        <Skeleton className="h-10 w-2/3" />
        <Skeleton className="h-5 w-1/2" />
        <Skeleton className="h-40 w-full" />
      </div>
    );

  return (
    <div className="space-y-6">
      <Link
        href={PATIENT_ROUTES.images}
        className="inline-flex min-h-11 items-center gap-1 rounded-md text-base font-medium text-primary underline-offset-4 hover:underline focus-visible:ring-2 focus-visible:ring-ring"
      >
        <ChevronLeft aria-hidden />
        Ảnh chụp
      </Link>
      <PortalPageHeader
        title={study.serviceName}
        description={`${study.patient.fullName} (${CARE_RELATIONSHIP_LABELS[study.patient.relationship]}) · Chụp ngày ${formatDate(study.studyDate)}`}
      />
      <PatientStudyDetail study={study} />
    </div>
  );
}
