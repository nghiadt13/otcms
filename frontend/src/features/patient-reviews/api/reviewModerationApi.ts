import { clone, mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import { toQueryString, type PageQuery } from "@/lib/api/pagination";
import { validateReviewReply } from "../schema/reviewReplyFieldRules";
import type {
  ReviewCounts,
  ReviewDetail,
  ReviewFilters,
  ReviewListItem,
  ReviewListPage,
  ReviewReplyRequest,
  ReviewStatus,
} from "../types/reviewModeration";
import {
  MOCK_CONFLICT_NN,
  MOCK_NOT_FOUND_NN,
  MOCK_REPLY_INVALID_NN,
  MOCK_SERVER_ERROR_NN,
  allowedActionsOf,
  reviewIdOf,
  reviewStore,
} from "./reviewModerationMockData";

// LỚP API GIẢ cho /api/v1/admin/reviews. Giữ nguyên chữ ký khi nối BE thật.

const DELAY_MS = 400;
export const DEFAULT_REVIEW_SORT = "submittedAt,desc";

/** Query gửi lên BE: giá trị rỗng bị bỏ, `replied=false` vẫn giữ. */
export function buildReviewListQuery(query: Partial<ReviewFilters> & PageQuery) {
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

function toListItem(review: ReviewDetail): ReviewListItem {
  const { reply, ...item } = review;
  void reply;
  return item;
}

function countsOf(): ReviewCounts {
  const all = reviewStore.reviews;
  const by = (status: ReviewStatus) =>
    all.filter((r) => r.status === status).length;
  return {
    pending: by("PENDING"),
    published: by("PUBLISHED"),
    hidden: by("HIDDEN"),
    all: all.length,
  };
}

function findReview(id: string): ReviewDetail {
  const found = reviewStore.reviews.find((r) => r.id === id);
  if (!found) mockFail(404, "MSG-50");
  return found;
}

export async function listModerationReviews(
  query: Partial<ReviewFilters> & PageQuery,
  signal?: AbortSignal,
): Promise<ReviewListPage> {
  await mockDelay(DELAY_MS, signal);
  // "Server" đọc lại đúng query string đã gửi.
  const params = new URLSearchParams(buildReviewListQuery(query));
  const from = params.get("from");
  const to = params.get("to");
  if (from && to && from > to) mockFail(400, "MSG-54");

  const status = params.get("status");
  const rating = params.get("rating");
  const replied = params.get("replied");
  const filtered = reviewStore.reviews
    .filter((r) => !status || r.status === status)
    .filter((r) => !rating || r.rating === Number(rating))
    .filter((r) => !replied || r.replied === (replied === "true"))
    .filter((r) => !from || r.submittedAt.slice(0, 10) >= from)
    .filter((r) => !to || r.submittedAt.slice(0, 10) <= to)
    .sort((a, b) => b.submittedAt.localeCompare(a.submittedAt));

  return clone(
    paginate(
      filtered.map(toListItem),
      {
        page: params.has("page") ? Number(params.get("page")) : undefined,
        size: params.has("size") ? Number(params.get("size")) : undefined,
      },
      { counts: countsOf() },
    ),
  );
}

export async function getModerationReview(id: string): Promise<ReviewDetail> {
  await mockDelay(DELAY_MS);
  if (id === reviewIdOf(MOCK_NOT_FOUND_NN)) mockFail(404, "MSG-50");
  if (id === reviewIdOf(MOCK_SERVER_ERROR_NN)) mockFail(500, "MSG-04");
  return clone(findReview(id));
}

type Decision = "approve" | "hide" | "unhide";

// Trạng thái hiện tại phải cho phép quyết định, nếu không → 409 MSG-56.
const ALLOWED_FROM: Record<Decision, ReviewStatus[]> = {
  approve: ["PENDING"],
  hide: ["PENDING", "PUBLISHED"],
  unhide: ["HIDDEN"],
};
const NEXT_STATUS: Record<Decision, ReviewStatus> = {
  approve: "PUBLISHED",
  hide: "HIDDEN",
  unhide: "PUBLISHED",
};

async function decide(id: string, decision: Decision): Promise<ReviewDetail> {
  await mockDelay(DELAY_MS);
  const review = findReview(id);
  if (
    id === reviewIdOf(MOCK_CONFLICT_NN) ||
    !ALLOWED_FROM[decision].includes(review.status)
  )
    mockFail(409, "MSG-56");
  review.status = NEXT_STATUS[decision];
  review.allowedActions = allowedActionsOf(review.status, review.reply !== null);
  return clone(review);
}

export const approveModerationReview = (id: string) => decide(id, "approve");
export const hideModerationReview = (id: string) => decide(id, "hide");
export const unhideModerationReview = (id: string) => decide(id, "unhide");

export async function replyModerationReview(
  id: string,
  body: ReviewReplyRequest,
): Promise<ReviewDetail> {
  await mockDelay(DELAY_MS);
  const review = findReview(id);
  if (id === reviewIdOf(MOCK_REPLY_INVALID_NN) || validateReviewReply(body.text))
    mockFail(400, "MSG-57", undefined, [{ field: "text", code: "MSG-57" }]);
  review.reply = { text: body.text.trim(), repliedAt: new Date().toISOString() };
  review.replied = true;
  review.allowedActions = allowedActionsOf(review.status, true);
  return clone(review);
}
