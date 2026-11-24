import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PortalEmptyState } from "@/components/patient/PortalEmptyState";
import { formatDate } from "@/lib/format/formatDate";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import type { LoadedItems } from "../hooks/useLoadedItems";
import type { ReviewableVisit } from "../types/myReview";
import { patientLabel } from "../utils/myReviewLabels";
import { ReviewListSkeleton, ReviewLoadError } from "./ReviewListStates";

/** Các lượt khám có thể đánh giá, mỗi dòng có nút Viết đánh giá dẫn tới form của lượt đó. */
export function ReviewableVisitList({
  state,
}: {
  state: LoadedItems<ReviewableVisit>;
}) {
  if (state.loading)
    return <ReviewListSkeleton label="Đang tải lượt khám có thể đánh giá" />;
  if (state.error)
    return (
      <ReviewLoadError
        message={`Không tải được lượt khám có thể đánh giá. ${state.error}`}
        onRetry={state.reload}
      />
    );
  if (state.items.length === 0)
    return (
      <PortalEmptyState
        className="max-sm:px-3 max-sm:py-5"
        action={
          <Link
            className={cn(publicButtonClass("secondary"), "w-full sm:w-auto")}
            href={PATIENT_ROUTES.visits}
          >
            Xem lượt khám
          </Link>
        }
      >
        Hiện chưa có lượt khám nào để đánh giá. Lượt khám của bạn sẽ hiện ở đây
        sau khi hoàn tất, nếu bạn chưa gửi đánh giá cho lượt đó.
      </PortalEmptyState>
    );

  return (
    <ul className="grid gap-2 sm:block sm:divide-y sm:overflow-hidden sm:rounded-lg sm:border sm:bg-card">
      {state.items.map((visit) => {
        const date = formatDate(visit.visitDate);
        return (
          <li
            className="grid min-w-0 gap-3 p-3 max-sm:rounded-lg max-sm:border max-sm:bg-card sm:flex sm:flex-wrap sm:items-center sm:justify-between sm:p-4"
            key={visit.visitId}
          >
            <div className="grid min-w-0 gap-1 text-base wrap-anywhere">
              <p className="font-semibold tabular-nums">
                Lượt khám ngày {date}
              </p>
              <p>{patientLabel(visit.patient)}</p>
              <p className="text-muted-foreground">{visit.serviceName}</p>
            </div>
            <Link
              aria-label={`Viết đánh giá lượt khám ngày ${date} của ${visit.patient.fullName}`}
              className={cn(publicButtonClass("secondary"), "w-full sm:w-auto")}
              href={PATIENT_ROUTES.newReview(visit.visitId)}
            >
              Viết đánh giá
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
