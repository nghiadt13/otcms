import { clone, mockDelay, paginate } from "@/lib/api/mockServer";
import type { PageQuery } from "@/lib/api/pagination";
import type {
  PublicReview,
  PublicReviewPage,
  ReviewSummary,
} from "../types/publicReview";
import type { ReviewDetail } from "../types/reviewModeration";
import { reviewStore } from "./reviewModerationMockData";

// LỚP API GIẢ cho /api/v1/public/reviews (không cần đăng nhập). Đọc cùng kho đánh giá với
// màn Admin: chỉ đánh giá PUBLISHED mới hiện, và ẩn đánh giá là biến mất khỏi đây (BR-55).
// Tên bệnh nhân không bao giờ được trả ra.

const LATENCY_MS = process.env.NODE_ENV === "test" ? 0 : 200;

function toPublicReview(review: ReviewDetail): PublicReview {
  return {
    id: review.id,
    rating: review.rating,
    text: review.content,
    createdAt: review.submittedAt,
    clinicReply: review.reply?.text ?? null,
  };
}

function summarize(reviews: ReviewDetail[]): ReviewSummary {
  if (reviews.length === 0) return { average: 0, count: 0 };
  const total = reviews.reduce((sum, review) => sum + review.rating, 0);
  return {
    average: Math.round((total / reviews.length) * 10) / 10,
    count: reviews.length,
  };
}

/** GET /api/v1/public/reviews?page=&size= — mới nhất trước, kèm `summary` của toàn bộ đánh giá đã duyệt. */
export async function listPublicReviews(
  query: PageQuery = {},
  signal?: AbortSignal,
): Promise<PublicReviewPage> {
  await mockDelay(LATENCY_MS, signal);
  const published = reviewStore.reviews
    .filter((review) => review.status === "PUBLISHED")
    .sort((a, b) => b.submittedAt.localeCompare(a.submittedAt));
  return clone(
    paginate(published.map(toPublicReview), query, {
      summary: summarize(published),
    }),
  );
}

/** Đánh giá nổi bật trên trang chủ: `limit` đánh giá mới nhất đã duyệt. */
export async function listFeaturedReviews(limit = 3): Promise<PublicReview[]> {
  const { items } = await listPublicReviews({ page: 0, size: limit });
  return items;
}
