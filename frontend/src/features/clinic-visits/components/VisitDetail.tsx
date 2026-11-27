"use client";

import Link from "next/link";
import { notFound } from "next/navigation";
import type { ReactNode } from "react";
import { ChevronLeft } from "lucide-react";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Skeleton } from "@/components/ui/skeleton";
import { isApiError } from "@/lib/api/apiError";
import { messageFromError } from "@/lib/messages/appMessages";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { useVisitProgress } from "../hooks/useVisitProgress";
import { useVisitSummary } from "../hooks/useVisitSummary";
import { VisitLoadError } from "./VisitLoadError";
import { VisitProgressView } from "./VisitProgressView";
import { VisitUpdateBar } from "./VisitUpdateBar";

// Chi tiết tiến độ một lượt khám (UC34). Lượt không có (404) hoặc ngoài phạm vi (403) đi tới trang
// không tìm thấy của khu, không lộ lý do. Dữ liệu tự tải lại khi tab hiện (xem useVisitProgress).
// Tiến độ từ BE không kèm tên người khám hay ngày khám: các dòng đó lấy thêm từ danh sách lượt (useVisitSummary).

export function VisitDetail({ id }: { id: string }) {
  const {
    progress,
    loading,
    error,
    refreshFailedAt,
    refreshing,
    reload,
    updatedAt,
  } = useVisitProgress(id);
  const summary = useVisitSummary(id, updatedAt);

  if (isApiError(error) && (error.status === 403 || error.status === 404))
    notFound();

  if (loading)
    return (
      <DetailFrame>
        <VisitDetailSkeleton />
      </DetailFrame>
    );
  if (error || !progress)
    return (
      <DetailFrame>
        <VisitLoadError
          message={`Không tải được lượt khám. ${messageFromError(error)}`}
          onRetry={reload}
        />
      </DetailFrame>
    );

  return (
    <DetailFrame>
      <VisitUpdateBar
        onRefresh={reload}
        refreshFailedAt={refreshFailedAt}
        refreshing={refreshing}
        updatedAt={updatedAt}
      />
      <VisitProgressView progress={progress} summary={summary} />
    </DetailFrame>
  );
}

/** Khung chung: đường về danh sách, tiêu đề (một h1) và nội dung. */
function DetailFrame({ children }: { children?: ReactNode }) {
  return (
    <div className="space-y-8">
      <Link
        className="inline-flex min-h-11 items-center gap-1 text-base font-medium text-primary underline-offset-4 hover:underline"
        href={PATIENT_ROUTES.visits}
      >
        <ChevronLeft aria-hidden className="size-5" />
        Lượt khám
      </Link>
      <PortalPageHeader title="Tiến độ lượt khám" />
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
