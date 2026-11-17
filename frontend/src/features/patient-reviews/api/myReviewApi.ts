import { apiRequest } from "@/lib/api/client";
import type { ItemsResponse } from "@/lib/api/pagination";
import type { MyReview, ReviewableVisit, SubmitReviewRequest } from "../types/myReview";
export function listEligibleVisits(signal?: AbortSignal): Promise<ItemsResponse<ReviewableVisit>> { return apiRequest("/api/v1/me/reviews/eligible-visits", { signal }); }
export function listMyReviews(signal?: AbortSignal): Promise<ItemsResponse<MyReview>> { return apiRequest("/api/v1/me/reviews", { signal }); }
export function submitReview(request: SubmitReviewRequest, signal?: AbortSignal): Promise<MyReview> { return apiRequest("/api/v1/reviews", { method: "POST", body: JSON.stringify(request), signal }); }
