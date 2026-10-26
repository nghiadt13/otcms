import { afterEach, describe, expect, it } from "vitest";
import {
  buildWebsiteContentSeed,
  resetWebsiteContentStore,
} from "./websiteContentAdminMockData";
import {
  getPublishedAboutPage,
  getPublishedHealthArticle,
} from "./websiteContentApi";

afterEach(() => {
  resetWebsiteContentStore();
});

describe("getPublishedAboutPage", () => {
  it("lấy trang giới thiệu đã đăng gần nhất", async () => {
    const page = await getPublishedAboutPage();
    expect(page?.title).toBe("Giới thiệu phòng khám");
    expect(page?.body).toContain("Về chúng tôi");
  });

  it("chưa đăng trang nào thì trả null, không lỗi", async () => {
    resetWebsiteContentStore(
      buildWebsiteContentSeed().filter((item) => item.type !== "ABOUT_PAGE"),
    );
    await expect(getPublishedAboutPage()).resolves.toBeNull();
  });
});

describe("getPublishedHealthArticle", () => {
  it("bài đã đăng trả nội dung công khai", async () => {
    const article = buildWebsiteContentSeed().find(
      (item) =>
        item.type === "HEALTH_ARTICLE" &&
        item.allowedActions.includes("viewOnWebsite"),
    )!;
    const detail = await getPublishedHealthArticle(article.id);
    expect(detail).toEqual({
      id: article.id,
      title: article.title,
      body: article.body,
      publishedAt: article.publishedAt,
    });
  });

  it("bài chưa có trang công khai hoặc không tồn tại trả 404 MSG-50", async () => {
    const unpublished = buildWebsiteContentSeed().find(
      (item) =>
        item.type === "HEALTH_ARTICLE" &&
        !item.allowedActions.includes("viewOnWebsite"),
    )!;
    await expect(
      getPublishedHealthArticle(unpublished.id),
    ).rejects.toMatchObject({
      status: 404,
      code: "MSG-50",
    });
    await expect(getPublishedHealthArticle("khong-co")).rejects.toMatchObject({
      status: 404,
      code: "MSG-50",
    });
  });
});
