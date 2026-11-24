import { StatusBadge } from "@/components/admin/StatusBadge";
import { PortalEmptyState } from "@/components/patient/PortalEmptyState";
import { formatDate } from "@/lib/format/formatDate";
import { STATUS_LABEL } from "../utils/reviewModerationLabels";
import type { LoadedItems } from "../hooks/useLoadedItems";
import type { MyReview } from "../types/myReview";
import { MY_REVIEW_STATUS_TONE, patientLabel } from "../utils/myReviewLabels";
import { ReviewRatingStars } from "./ReviewRatingStars";
import { ReviewListSkeleton, ReviewLoadError } from "./ReviewListStates";

/** Đánh giá đã gửi: điểm, nội dung, trạng thái duyệt và phản hồi của phòng khám nếu có. */
export function MyReviewList({ state }: { state: LoadedItems<MyReview> }) {
  if (state.loading)
    return <ReviewListSkeleton label="Đang tải đánh giá đã gửi" />;
  if (state.error)
    return (
      <ReviewLoadError
        message={`Không tải được đánh giá đã gửi. ${state.error}`}
        onRetry={state.reload}
      />
    );
  if (state.items.length === 0)
    return (
      <PortalEmptyState className="max-sm:px-3 max-sm:py-5">
        Bạn chưa gửi đánh giá nào. Sau khi viết đánh giá, trạng thái duyệt sẽ
        hiện ở đây.
      </PortalEmptyState>
    );

  return (
    <ul className="grid gap-2 sm:block sm:divide-y sm:overflow-hidden sm:rounded-lg sm:border sm:bg-card">
      {state.items.map((review) => (
        <li
          className="grid min-w-0 gap-2 p-3 text-base wrap-anywhere max-sm:rounded-lg max-sm:border max-sm:bg-card sm:gap-3 sm:p-4"
          key={review.id}
        >
          <div className="flex flex-wrap items-start justify-between gap-2">
            <div className="grid min-w-0 gap-1">
              <p className="font-semibold tabular-nums">
                Lượt khám ngày {formatDate(review.visitDate)}
              </p>
              <p>{patientLabel(review.patient)}</p>
            </div>
            <StatusBadge className="shrink-0" tone={MY_REVIEW_STATUS_TONE[review.status]}>
              {STATUS_LABEL[review.status]}
            </StatusBadge>
          </div>
          <ReviewRatingStars rating={review.rating} />
          {review.text ? (
            <p className="leading-relaxed sm:leading-7">{review.text}</p>
          ) : null}
          {review.clinicReply ? (
            <div className="rounded-lg bg-secondary p-3 sm:p-4">
              <p className="text-sm font-semibold text-secondary-foreground">
                Phòng khám phản hồi
              </p>
              <p className="mt-1 leading-relaxed text-secondary-foreground sm:leading-7">
                {review.clinicReply}
              </p>
            </div>
          ) : null}
          <p className="text-sm text-muted-foreground">
            Gửi ngày {formatDate(review.createdAt)}
          </p>
        </li>
      ))}
    </ul>
  );
}
