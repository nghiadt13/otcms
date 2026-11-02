import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PortalEmptyState } from "@/components/patient/PortalEmptyState";
import { formatDate } from "@/lib/format/formatDate";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
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
        action={
          <Link
            className={publicButtonClass("secondary")}
            href={PATIENT_ROUTES.visits}
          >
            Xem lượt khám
          </Link>
        }
      >
        Hiện chưa có lượt khám nào để đánh giá. Lượt khám đã hoàn tất sẽ hiện ở
        đây để bạn đánh giá.
      </PortalEmptyState>
    );

  return (
    <ul className="divide-y overflow-hidden rounded-lg border bg-card">
      {state.items.map((visit) => {
        const date = formatDate(visit.visitDate);
        return (
          <li
            className="flex flex-wrap items-center justify-between gap-3 px-4 py-4"
            key={visit.visitId}
          >
            <div className="grid min-w-0 gap-1 text-base">
              <p className="font-semibold tabular-nums">
                Lượt khám ngày {date}
              </p>
              <p className="break-words">{patientLabel(visit.patient)}</p>
              <p className="text-muted-foreground">{visit.serviceName}</p>
            </div>
            <Link
              aria-label={`Viết đánh giá lượt khám ngày ${date} của ${visit.patient.fullName}`}
              className={publicButtonClass("secondary")}
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
