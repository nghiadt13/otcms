import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import {
  getPublishedAboutPage,
  getPublishedHealthArticle,
  getPublishedWebsiteContent,
} from "./websiteContentApi";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ publicApiRequest: request }));
beforeEach(() => {
  request.mockReset();
});
it("reads homepage and published articles from public endpoints", async () => {
  const content = {
    contact: {},
    hero: {},
    articles: [{ id: "a", imageUrl: "/api/v1/public/website-images/i" }],
  };
  request.mockResolvedValue(content);
  expect((await getPublishedWebsiteContent()).articles?.[0].imageUrl).toContain(
    "/api/v1/public/website-images/i",
  );
  expect(request.mock.calls[0][0]).toBe("/api/v1/public/website-content");
  request.mockResolvedValue({ id: "a", title: "Bài viết", body: "Nội dung" });
  await getPublishedHealthArticle("a");
  expect(request.mock.calls.at(-1)![0]).toBe("/api/v1/public/articles/a");
});
it("handles an absent about page without hiding network or authorization failures", async () => {
  request.mockRejectedValueOnce(new ApiError(404, { errorCode: "MSG-50" }));
  await expect(getPublishedAboutPage()).resolves.toBeNull();
  request.mockRejectedValueOnce(new ApiError(503, { errorCode: "MSG-04" }));
  await expect(getPublishedAboutPage()).rejects.toMatchObject({
    status: 503,
    code: "MSG-04",
  });
});
