import { MessageSquareReply, Star } from "lucide-react";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { PublicSectionHeading } from "@/components/brand/PublicSectionHeading";
import { Card } from "@/components/ui/card";
import { formatDate } from "@/lib/format/formatDate";
import { cn } from "@/lib/utils";
import type { PublicReview } from "../types/publicReview";

// Khối "Bệnh nhân nói gì": đánh giá đầu tiên là thẻ lớn bên trái (kèm phản hồi
// của phòng khám nếu có, BR-56), hai đánh giá sau xếp chồng bên phải. Chỉ hiện
// đánh giá đã công khai; không có thì ẩn khối.
export function FeaturedReviews({ reviews }: { reviews: PublicReview[] }) {
  if (reviews.length === 0) return null;
  const [highlight, ...others] = reviews;
  return (
    <section aria-labelledby="reviews-title" className="py-20 lg:py-28">
      <div className={publicContainerClass}>
        <PublicSectionHeading
          description="Trích từ đánh giá bệnh nhân gửi sau lượt khám."
          id="reviews-title"
          title="Bệnh nhân nói gì về chúng tôi"
        />
        <div className="mt-12 grid gap-6 lg:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
          <ReviewCard large review={highlight} />
          <div className="grid gap-6">
            {others.map((review) => (
              <ReviewCard key={review.id} review={review} />
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

function ReviewCard({
  review,
  large = false,
}: {
  review: PublicReview;
  large?: boolean;
}) {
  return (
    <Card
      className={cn(
        "gap-0 rounded-2xl p-8 text-base shadow-sm",
        large && "lg:p-10",
      )}
    >
      <figure className="flex h-full flex-col">
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
        {review.text ? (
          <blockquote
            className={cn(
              "mt-5 leading-8 text-pretty text-foreground",
              large ? "text-xl xl:text-2xl xl:leading-10" : "text-lg",
            )}
          >
            “{review.text}”
          </blockquote>
        ) : null}
        <figcaption className="mt-6 text-base text-muted-foreground">
          <time dateTime={review.createdAt}>
            {formatDate(review.createdAt)}
          </time>
        </figcaption>
        {large && review.clinicReply && (
          <div className="mt-auto pt-8">
            <div className="rounded-xl bg-secondary p-5">
              <p className="flex items-center gap-2 text-sm font-semibold text-secondary-foreground">
                <MessageSquareReply aria-hidden className="size-4" />
                Phản hồi từ phòng khám
              </p>
              <p className="mt-2 text-base leading-7 text-secondary-foreground">
                {review.clinicReply}
              </p>
            </div>
          </div>
        )}
      </figure>
    </Card>
  );
}
