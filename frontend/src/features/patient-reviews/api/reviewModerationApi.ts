import { apiRequest } from "@/lib/api/client";
import { toQueryString, type PageQuery } from "@/lib/api/pagination";
import type {
  ReviewDetail,
  ReviewFilters,
  ReviewListPage,
  ReviewReplyRequest,
} from "../types/reviewModeration";
export const DEFAULT_REVIEW_SORT = "submittedAt,desc";
export function buildReviewListQuery(
  query: Partial<ReviewFilters> & PageQuery,
) {
  return toQueryString({
    status: query.status,
    rating: query.rating,
    replied: query.replied,
    from: query.from,
    to: query.to,
    page: query.page,
    size: query.size,
    sort: query.sort ?? DEFAULT_REVIEW_SORT,
  });
}
export function listModerationReviews(
  query: Partial<ReviewFilters> & PageQuery,
  signal?: AbortSignal,
): Promise<ReviewListPage> {
  return apiRequest(`/api/v1/admin/reviews${buildReviewListQuery(query)}`, {
    signal,
  });
}
export function getModerationReview(id: string): Promise<ReviewDetail> {
  return apiRequest(`/api/v1/admin/reviews/${encodeURIComponent(id)}`);
}
function decide(
  id: string,
  decision: "approve" | "hide" | "unhide",
): Promise<ReviewDetail> {
  return apiRequest(
    `/api/v1/admin/reviews/${encodeURIComponent(id)}/${decision}`,
    { method: "POST" },
  );
}
export const approveModerationReview = (id: string) => decide(id, "approve");
export const hideModerationReview = (id: string) => decide(id, "hide");
export const unhideModerationReview = (id: string) => decide(id, "unhide");
export function replyModerationReview(
  id: string,
  body: ReviewReplyRequest,
): Promise<ReviewDetail> {
  return apiRequest(`/api/v1/admin/reviews/${encodeURIComponent(id)}/reply`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}
