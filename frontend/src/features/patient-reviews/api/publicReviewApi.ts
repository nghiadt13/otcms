import { publicApiRequest } from "@/lib/api/client";
import { toQueryString, type PageQuery } from "@/lib/api/pagination";
import type { PublicReview, PublicReviewPage } from "../types/publicReview";
export function listPublicReviews(
  query: PageQuery = {},
  signal?: AbortSignal,
): Promise<PublicReviewPage> {
  return publicApiRequest(`/api/v1/public/reviews${toQueryString(query)}`, {
    signal,
  });
}
export async function listFeaturedReviews(limit = 3): Promise<PublicReview[]> {
  const { items } = await listPublicReviews({ page: 0, size: limit });
  return items;
}
