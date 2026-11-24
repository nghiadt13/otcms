import {
  CircleAlert,
  Clock3,
  MessageSquareReply,
  Quote,
  Star,
} from "lucide-react";
import Link from "next/link";
import { Suspense } from "react";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { PublicSectionHeading } from "@/components/brand/PublicSectionHeading";
import { buttonVariants } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { formatDate } from "@/lib/format/formatDate";
import { cn } from "@/lib/utils";
import { listPublicReviews } from "../api/publicReviewApi";
import type { PublicReview } from "../types/publicReview";

const REVIEW_COUNT = 3;
const REVIEW_TIMEOUT_MS = 1_500;

const placeholderText = {
  loading: "Đang tải đánh giá",
  ready: "Đánh giá đang chờ cập nhật",
  unavailable: "Chưa tải được dữ liệu đánh giá",
};

export function FeaturedReviewsSection() {
  return (
    <Suspense fallback={<FeaturedReviews reviews={[]} state="loading" />}>
      <FeaturedReviewsContent />
    </Suspense>
  );
}

async function FeaturedReviewsContent() {
  const page = await listPublicReviews(
    { page: 0, size: REVIEW_COUNT, sort: "submittedAt,desc" },
    AbortSignal.timeout(REVIEW_TIMEOUT_MS),
  ).catch(() => null);

  return page ? (
    <FeaturedReviews reviews={page.items} />
  ) : (
    <FeaturedReviews reviews={[]} state="unavailable" />
  );
}

export function FeaturedReviews({
  reviews,
  state = "ready",
}: {
  reviews: PublicReview[];
  state?: keyof typeof placeholderText;
}) {
  const PlaceholderIcon = state === "unavailable" ? CircleAlert : Clock3;

  return (
    <section
      aria-labelledby="reviews-title"
      className="bg-secondary py-10 lg:py-14"
    >
      <div className={publicContainerClass}>
        <PublicSectionHeading
          className="items-center justify-center text-center lg:items-center lg:justify-center [&_p]:mt-2"
          description="Trích từ đánh giá bệnh nhân gửi sau lượt khám."
          id="reviews-title"
          title="Bệnh nhân nói gì về chúng tôi"
        />
        <ul
          aria-busy={state === "loading" ? true : undefined}
          className="mt-6 grid auto-rows-fr gap-4 lg:grid-cols-3"
        >
          {Array.from({ length: REVIEW_COUNT }, (_, index) => {
            const review = reviews[index];
            return (
              <li
                key={review?.id ?? `placeholder-${index}`}
                className="min-w-0"
              >
                <Card className="h-full min-h-36 gap-0 rounded-[12px] border bg-card px-5 py-4 text-base shadow-sm ring-0 lg:min-h-44">
                  {review ? (
                    <ReviewCard review={review} />
                  ) : (
                    <div className="flex flex-1 flex-col items-center justify-center gap-3 text-center text-muted-foreground">
                      <PlaceholderIcon
                        aria-hidden
                        className="size-6 text-primary"
                      />
                      <p>{placeholderText[state]}</p>
                    </div>
                  )}
                </Card>
              </li>
            );
          })}
        </ul>
        <div className="mt-6 flex justify-center">
          <Link
            className={buttonVariants({ className: "h-11 px-5" })}
            href="/reviews"
          >
            Xem tất cả đánh giá
          </Link>
        </div>
      </div>
    </section>
  );
}

function ReviewCard({ review }: { review: PublicReview }) {
  return (
    <figure className="flex h-full flex-col gap-3">
      <div className="flex items-center justify-between gap-3">
        <Quote aria-hidden className="size-6 text-primary" />
        <p
          aria-label={`${review.rating} trên 5 sao`}
          className="flex gap-1 text-rating"
          role="img"
        >
          {Array.from({ length: 5 }, (_, index) => (
            <Star
              aria-hidden
              className={cn(
                "size-5",
                index < review.rating
                  ? "fill-current"
                  : "text-muted-foreground",
              )}
              key={index}
            />
          ))}
        </p>
      </div>
      {review.text ? (
        <blockquote className="wrap-break-word text-base leading-7 text-foreground">
          {review.text}
        </blockquote>
      ) : (
        <p className="text-muted-foreground">Bệnh nhân chỉ chấm sao.</p>
      )}
      {review.clinicReply ? (
        <div className="rounded-lg bg-secondary px-3 py-2 text-secondary-foreground">
          <p className="flex items-center gap-2 text-sm font-semibold">
            <MessageSquareReply aria-hidden className="size-4 shrink-0" />
            Phản hồi từ phòng khám
          </p>
          <p className="mt-1 wrap-break-word text-base leading-6">
            {review.clinicReply}
          </p>
        </div>
      ) : null}
      <figcaption className="mt-auto pt-1 text-sm text-muted-foreground">
        <time dateTime={review.createdAt}>{formatDate(review.createdAt)}</time>
      </figcaption>
    </figure>
  );
}
