"use client";

import Link from "next/link";
import { notFound } from "next/navigation";
import type { ReactNode } from "react";
import { ChevronLeft } from "lucide-react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Skeleton } from "@/components/ui/skeleton";
import { isApiError } from "@/lib/api/apiError";
import {
  VISIT_STATUS_LABELS,
  VISIT_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
import { formatDate } from "@/lib/format/formatDate";
import { messageFromError } from "@/lib/messages/appMessages";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { useVisitProgress } from "../hooks/useVisitProgress";
import type { VisitProgress } from "../types/visitProgress";
import { VisitLoadError } from "./VisitLoadError";
import { VisitProgressView } from "./VisitProgressView";
import { VisitUpdateBar } from "./VisitUpdateBar";
import { visitPatientLabel } from "./visitPresentation";

// Chi tiết tiến độ một lượt khám (UC34). Lượt không có (404) hoặc ngoài phạm vi (403) đi tới trang
// không tìm thấy của khu, không lộ lý do. Dữ liệu tự tải lại khi tab hiện (xem useVisitProgress).

export function VisitDetail({ id }: { id: string }) {
  const { progress, loading, error, refreshFailedAt, refreshing, reload } =
    useVisitProgress(id);

  if (isApiError(error) && (error.status === 403 || error.status === 404))
    notFound();

  if (loading)
    return (
      <DetailFrame title="Lượt khám">
        <VisitDetailSkeleton />
      </DetailFrame>
    );
  if (error || !progress)
    return (
      <DetailFrame title="Lượt khám">
        <VisitLoadError
          message={`Không tải được lượt khám. ${messageFromError(error)}`}
          onRetry={reload}
        />
      </DetailFrame>
    );

  return (
    <DetailFrame
      progress={progress}
      title={`Lượt khám ngày ${formatDate(progress.visitDate)}`}
    >
      <VisitUpdateBar
        onRefresh={reload}
        refreshFailedAt={refreshFailedAt}
        refreshing={refreshing}
        updatedAt={progress.updatedAt}
      />
      <VisitProgressView progress={progress} />
    </DetailFrame>
  );
}

/** Khung chung: đường về danh sách, tiêu đề (một h1), người khám và trạng thái, nội dung. */
function DetailFrame({
  title,
  progress,
  children,
}: {
  title: string;
  progress?: VisitProgress;
  children?: ReactNode;
}) {
  return (
    <div className="space-y-8">
      <Link
        className="inline-flex min-h-11 items-center gap-1 text-base font-medium text-primary underline-offset-4 hover:underline"
        href={PATIENT_ROUTES.visits}
      >
        <ChevronLeft aria-hidden className="size-5" />
        Lượt khám
      </Link>
      <PortalPageHeader
        description={
          progress ? (
            <span className="flex flex-wrap items-center gap-x-3 gap-y-2">
              <span className="break-words">
                {visitPatientLabel(progress.patient)}
              </span>
              <StatusBadge tone={VISIT_STATUS_TONE[progress.status]}>
                {VISIT_STATUS_LABELS[progress.status]}
              </StatusBadge>
            </span>
          ) : undefined
        }
        title={title}
      />
      {children}
    </div>
  );
}

function VisitDetailSkeleton() {
  return (
    <div aria-busy="true" className="space-y-4" role="status">
      <span className="sr-only">Đang tải lượt khám</span>
      <Skeleton className="h-6 w-40" />
      <Skeleton className="h-28 w-full" />
      <Skeleton className="h-32 w-full" />
    </div>
  );
}
