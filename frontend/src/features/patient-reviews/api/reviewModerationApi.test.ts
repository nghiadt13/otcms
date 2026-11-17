import { expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { listModerationReviews, getModerationReview, approveModerationReview, hideModerationReview, unhideModerationReview, replyModerationReview } from "./reviewModerationApi";
vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
it("sends filters, decisions and reply with the backend methods", async () => {
  const request = vi.mocked(apiRequest).mockResolvedValue({});
  await listModerationReviews({ replied: "false" }); await getModerationReview("id"); await approveModerationReview("id"); await hideModerationReview("id"); await unhideModerationReview("id"); await replyModerationReview("id", { text: "reply" });
  expect(request).toHaveBeenNthCalledWith(1, "/api/v1/admin/reviews?replied=false&sort=submittedAt%2Cdesc", { signal: undefined });
  expect(request).toHaveBeenNthCalledWith(2, "/api/v1/admin/reviews/id");
  expect(request).toHaveBeenNthCalledWith(3, "/api/v1/admin/reviews/id/approve", { method: "POST" });
  expect(request).toHaveBeenNthCalledWith(4, "/api/v1/admin/reviews/id/hide", { method: "POST" });
  expect(request).toHaveBeenNthCalledWith(5, "/api/v1/admin/reviews/id/unhide", { method: "POST" });
  expect(request).toHaveBeenNthCalledWith(6, "/api/v1/admin/reviews/id/reply", { method: "PUT", body: JSON.stringify({ text: "reply" }) });
});
