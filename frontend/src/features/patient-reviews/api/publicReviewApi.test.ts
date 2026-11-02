import { beforeEach, describe, expect, it } from "vitest";
import { listFeaturedReviews, listPublicReviews } from "./publicReviewApi";
import {
  reviewIdOf,
  reviewStore,
  resetReviewModerationMockData,
} from "./reviewModerationMockData";

const publishedOf = () =>
  reviewStore.reviews.filter((r) => r.status === "PUBLISHED");

beforeEach(() => {
  resetReviewModerationMockData();
});

describe("listPublicReviews", () => {
  it("chỉ trả đánh giá đã duyệt; đánh giá ẩn không hiện (BR-55)", async () => {
    const page = await listPublicReviews({ page: 0, size: 100 });
    expect(page.totalItems).toBe(publishedOf().length);
    const ids = page.items.map((item) => item.id);
    expect(ids).not.toContain(reviewIdOf(14)); // HIDDEN nhưng có phản hồi
    expect(ids).toContain(reviewIdOf(15)); // PUBLISHED có phản hồi
  });

  it("phản hồi của phòng khám chỉ đi cùng đánh giá đang công khai (BR-56)", async () => {
    const page = await listPublicReviews({ page: 0, size: 100 });
    const replied = page.items.find((item) => item.id === reviewIdOf(15));
    expect(replied?.clinicReply).toBe(
      reviewStore.reviews.find((r) => r.id === reviewIdOf(15))?.reply?.text,
    );
    const unreplied = page.items.find((item) => item.id === reviewIdOf(7));
    expect(unreplied?.clinicReply).toBeNull();
  });

  it("không trả tên bệnh nhân hay thông tin lượt khám", async () => {
    const page = await listPublicReviews({ page: 0, size: 5 });
    for (const item of page.items) {
      expect(Object.keys(item).sort()).toEqual(
        ["clinicReply", "createdAt", "id", "rating", "text"].sort(),
      );
    }
  });

  it("phân trang và tính điểm trung bình trên toàn bộ đánh giá đã duyệt", async () => {
    const published = publishedOf();
    const average =
      Math.round(
        (published.reduce((sum, r) => sum + r.rating, 0) / published.length) *
          10,
      ) / 10;

    const page = await listPublicReviews({ page: 1, size: 5 });
    expect(page.items).toHaveLength(5);
    expect(page.page).toBe(1);
    expect(page.totalPages).toBe(Math.ceil(published.length / 5));
    expect(page.summary).toEqual({ average, count: published.length });
  });

  it("ẩn đánh giá trong kho quản trị thì biến khỏi danh sách công khai", async () => {
    const target = reviewStore.reviews.find((r) => r.id === reviewIdOf(15))!;
    target.status = "HIDDEN";
    const page = await listPublicReviews({ page: 0, size: 100 });
    expect(page.items.map((item) => item.id)).not.toContain(reviewIdOf(15));
  });
});

describe("listFeaturedReviews", () => {
  it("lấy số đánh giá mới nhất theo yêu cầu", async () => {
    const featured = await listFeaturedReviews(2);
    expect(featured).toHaveLength(2);
    expect(featured[0].createdAt >= featured[1].createdAt).toBe(true);
  });
});
