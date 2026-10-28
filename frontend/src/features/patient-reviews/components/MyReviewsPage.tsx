"use client";

import { useEligibleVisits } from "../hooks/useEligibleVisits";
import { useMyReviews } from "../hooks/useMyReviews";
import { MyReviewList } from "./MyReviewList";
import { ReviewableVisitList } from "./ReviewableVisitList";

/** Nội dung trang Đánh giá: lượt khám có thể đánh giá, rồi đánh giá đã gửi. Hai khối tải riêng. */
export function MyReviewsPage() {
  const eligible = useEligibleVisits();
  const sent = useMyReviews();

  return (
    <div className="space-y-10">
      <section aria-labelledby="eligible-visits-heading" className="space-y-4">
        <h2 className="text-lg font-semibold" id="eligible-visits-heading">
          Lượt khám có thể đánh giá
        </h2>
        <ReviewableVisitList state={eligible} />
      </section>
      <section aria-labelledby="sent-reviews-heading" className="space-y-4">
        <h2 className="text-lg font-semibold" id="sent-reviews-heading">
          Đánh giá đã gửi
        </h2>
        <MyReviewList state={sent} />
      </section>
    </div>
  );
}
