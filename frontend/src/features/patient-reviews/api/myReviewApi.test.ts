import { beforeEach, expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { listEligibleVisits, listMyReviews, submitReview } from "./myReviewApi";
vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
beforeEach(() => vi.clearAllMocks());
it("uses authenticated endpoints and forwards review payload", async () => {
  const request = vi.mocked(apiRequest).mockResolvedValue({ items: [] });
  const signal = new AbortController().signal;
  await listEligibleVisits(signal);
  await listMyReviews(signal);
  const body = { visitId: "visit", rating: 5, text: "A good visit" };
  await submitReview(body, signal);
  expect(request).toHaveBeenNthCalledWith(
    1,
    "/api/v1/me/reviews/eligible-visits",
    { signal },
  );
  expect(request).toHaveBeenNthCalledWith(2, "/api/v1/me/reviews", { signal });
  expect(request).toHaveBeenNthCalledWith(3, "/api/v1/reviews", {
    method: "POST",
    body: JSON.stringify(body),
    signal,
  });
});
