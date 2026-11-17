import { expect, it, vi } from "vitest";
import { publicApiRequest } from "@/lib/api/client";
import { listFeaturedReviews, listPublicReviews } from "./publicReviewApi";
vi.mock("@/lib/api/client", () => ({ publicApiRequest: vi.fn() }));
it("passes paging and sorting to the public endpoint", async () => {
  const request = vi.mocked(publicApiRequest).mockResolvedValue({ items: [] });
  await listPublicReviews({ page: 1, sort: "rating,desc" });
  await listFeaturedReviews(3);
  expect(request).toHaveBeenNthCalledWith(1, "/api/v1/public/reviews?page=1&sort=rating%2Cdesc", { signal: undefined });
  expect(request).toHaveBeenNthCalledWith(2, "/api/v1/public/reviews?page=0&size=3", { signal: undefined });
});
