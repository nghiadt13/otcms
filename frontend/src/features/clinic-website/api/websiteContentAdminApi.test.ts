import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import {
  listWebsiteContents,
  getWebsiteContent,
  createWebsiteContent,
  updateWebsiteContent,
  uploadWebsiteImage,
  MAX_IMAGE_BYTES,
} from "./websiteContentAdminApi";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
beforeEach(() => {
  request.mockReset();
});
const article = {
  id: "article",
  type: "HEALTH_ARTICLE",
  title: "Bài viết",
  body: "Nội dung",
  imageUrl: null,
  allowedActions: ["edit", "viewOnWebsite"],
};
it("uses admin endpoints for server pagination, detail and mutations", async () => {
  request.mockResolvedValue({
    items: [],
    totalPages: 0,
    totalItems: 0,
    page: 1,
    size: 20,
  });
  await listWebsiteContents({
    type: "HEALTH_ARTICLE",
    q: "cột sống & vai",
    page: 1,
    size: 20,
  });
  const query = new URL(request.mock.calls[0][0], "http://local").searchParams;
  expect(Object.fromEntries(query)).toEqual({
    type: "HEALTH_ARTICLE",
    q: "cột sống & vai",
    page: "1",
    size: "20",
  });
  request.mockResolvedValue(article);
  await getWebsiteContent("article");
  expect(request.mock.calls.at(-1)![0]).toBe(
    "/api/v1/admin/website-contents/article",
  );
  const input = {
    type: "HEALTH_ARTICLE" as const,
    title: "Bài viết",
    body: "Nội dung",
    imageUrl: null,
  };
  await createWebsiteContent(input);
  expect(request).toHaveBeenLastCalledWith("/api/v1/admin/website-contents", {
    method: "POST",
    body: JSON.stringify(input),
  });
  await updateWebsiteContent("article", { title: "Sửa", body: "Mới" });
  expect(request.mock.calls.at(-1)![1].method).toBe("PUT");
});
it("uploads multipart file and normalizes the backend image URL", async () => {
  expect(MAX_IMAGE_BYTES).toBe(2 * 1024 * 1024);
  request.mockResolvedValue({ url: "/api/v1/public/website-images/id" });
  const file = new File(["png"], "cover.png", { type: "image/png" });
  const result = await uploadWebsiteImage(file);
  expect(request.mock.calls[0][1].body.get("file")).toBe(file);
  expect(result.url).toContain("/api/v1/public/website-images/id");
});
it("preserves real MSG error envelopes instead of manufacturing content", async () => {
  request.mockRejectedValue(new ApiError(400, { errorCode: "MSG-69" }));
  await expect(
    createWebsiteContent({ type: "ABOUT_PAGE", title: "", body: "" }),
  ).rejects.toMatchObject({ status: 400, code: "MSG-69" });
});
