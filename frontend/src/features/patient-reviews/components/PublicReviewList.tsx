import { MessageSquareReply } from "lucide-react";
import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { formatDate } from "@/lib/format/formatDate";
import { formatDecimal1 } from "@/lib/format/formatNumber";
import type { PublicReview, PublicReviewPage } from "../types/publicReview";
import { ReviewRatingStars } from "./ReviewRatingStars";

// Danh sách đánh giá công khai (UC60): điểm trung bình và số đánh giá đã duyệt, rồi từng
// đánh giá kèm ngày và phản hồi của phòng khám. Phân trang bằng "Xem thêm" (?page=, 1-based).
export function PublicReviewList({ page }: { page: PublicReviewPage }) {
  const { items, summary } = page;
  if (summary.count === 0) {
    return (
      <div className="mt-12 max-w-2xl rounded-2xl border bg-card p-8">
        <p className="text-lg font-semibold">Chưa có đánh giá nào.</p>
        <p className="mt-2 text-base leading-7 text-muted-foreground">
          Đánh giá sẽ hiện ở đây sau khi phòng khám duyệt.
        </p>
      </div>
    );
  }

  const hasNextPage = page.page + 1 < page.totalPages;
  return (
    <div className="mt-10">
      <p className="text-lg">
        Điểm trung bình{" "}
        <span className="font-semibold text-primary tabular-nums">
          {formatDecimal1(summary.average)}/5
        </span>{" "}
        từ <span className="tabular-nums">{summary.count}</span> đánh giá đã
        duyệt
      </p>

      <ul className="mt-8 divide-y divide-border overflow-hidden rounded-2xl border bg-card">
        {items.map((review) => (
          <li className="px-6 py-7 lg:px-8" key={review.id}>
            <ReviewItem review={review} />
          </li>
        ))}
      </ul>

      {hasNextPage ? (
        <Link
          className={publicButtonClass("secondary") + " mt-8"}
          href={`/reviews?page=${page.page + 2}`}
        >
          Xem thêm đánh giá
        </Link>
      ) : null}
    </div>
  );
}

function ReviewItem({ review }: { review: PublicReview }) {
  return (
    <article>
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-base">
        <ReviewRatingStars rating={review.rating} />
        <time className="text-muted-foreground" dateTime={review.createdAt}>
          {formatDate(review.createdAt)}
        </time>
      </div>
      {review.text ? (
        <p className="mt-4 text-lg leading-8 text-pretty">{review.text}</p>
      ) : null}
      {review.clinicReply ? (
        <div className="mt-5 rounded-xl bg-secondary p-5">
          <p className="flex items-center gap-2 text-base font-semibold text-secondary-foreground">
            <MessageSquareReply aria-hidden className="size-5" />
            Phản hồi từ phòng khám
          </p>
          <p className="mt-2 text-base leading-7 text-secondary-foreground">
            {review.clinicReply}
          </p>
        </div>
      ) : null}
    </article>
  );
}
