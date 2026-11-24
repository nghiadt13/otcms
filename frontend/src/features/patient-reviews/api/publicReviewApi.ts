import { publicApiRequest } from "@/lib/api/client";
import { toQueryString, type PageQuery } from "@/lib/api/pagination";
import type { PublicReview, PublicReviewPage } from "../types/publicReview";

const FEATURED_REVIEWS_TIMEOUT_MS = 1_500;

export function listPublicReviews(
  query: PageQuery = {},
  signal?: AbortSignal,
): Promise<PublicReviewPage> {
  return publicApiRequest(`/api/v1/public/reviews${toQueryString(query)}`, {
    signal,
  });
}

export async function listFeaturedReviews(limit = 3): Promise<PublicReview[]> {
  try {
    const { items } = await listPublicReviews(
      { page: 0, size: limit, sort: "submittedAt,desc" },
      AbortSignal.timeout(FEATURED_REVIEWS_TIMEOUT_MS),
    );
    return items.slice(0, limit);
  } catch {
    return [];
  }
}
