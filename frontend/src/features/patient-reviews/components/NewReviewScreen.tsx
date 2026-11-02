"use client";

import { useEffect, useRef } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiError } from "@/lib/api/apiError";
import { formatDate } from "@/lib/format/formatDate";
import { notifyError } from "@/lib/messages/notify";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { useEligibleVisits } from "../hooks/useEligibleVisits";
import { patientLabel } from "../utils/myReviewLabels";
import { ReviewForm } from "./ReviewForm";
import { ReviewLoadError } from "./ReviewListStates";

/** Khung chờ của trang Viết đánh giá, đúng hình tiêu đề và form. */
export function ReviewFormSkeleton() {
  return (
    <div aria-busy="true" className="grid gap-6" role="status">
      <span className="sr-only">Đang tải lượt khám</span>
      <Skeleton className="h-8 w-2/3" />
      <Skeleton className="h-5 w-1/2" />
      <Skeleton className="h-48 w-full rounded-lg" />
    </div>
  );
}

/**
 * Trang Viết đánh giá (`/portal/reviews/new?visitId=`). Chỉ mở form với lượt có thể đánh giá.
 * Thiếu mã, lượt không đủ điều kiện hoặc đã đánh giá: báo MSG-56 và về danh sách.
 */
export function NewReviewScreen() {
  const router = useRouter();
  const visitId = useSearchParams().get("visitId")?.trim() ?? "";
  const eligible = useEligibleVisits();
  const visit = eligible.items.find((item) => item.visitId === visitId);
  // Chỉ kết luận "không đủ điều kiện" khi đã có danh sách: không lỗi và không đang tải.
  const notEligible =
    visitId === "" || (!eligible.loading && !eligible.error && !visit);
  const redirected = useRef(false);

  useEffect(() => {
    if (!notEligible || redirected.current) return;
    redirected.current = true;
    notifyError(new ApiError(409, { code: "MSG-56" }));
    router.replace(PATIENT_ROUTES.reviews);
  }, [notEligible, router]);

  if (eligible.loading || notEligible) return <ReviewFormSkeleton />;
  if (eligible.error)
    return (
      <ReviewLoadError
        message={`Không tải được lượt khám. ${eligible.error}`}
        onRetry={eligible.reload}
      />
    );
  if (!visit) return null;

  return (
    <>
      <PortalPageHeader
        description={`${patientLabel(visit.patient)} · ${visit.serviceName}`}
        title={`Đánh giá lượt khám ngày ${formatDate(visit.visitDate)}`}
      />
      <ReviewForm visit={visit} />
    </>
  );
}
