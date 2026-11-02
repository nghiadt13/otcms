import { Star } from "lucide-react";

/** Điểm đánh giá: một icon sao (token rating) + "n/5" bằng chữ, nên không phụ thuộc màu. */
export function ReviewRatingStars({ rating }: { rating: number }) {
  return (
    <span className="inline-flex items-center gap-1 whitespace-nowrap tabular-nums">
      <Star aria-hidden className="size-4 fill-rating text-rating" />
      {rating}/5
    </span>
  );
}
