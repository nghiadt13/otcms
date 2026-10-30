import { beforeEach, describe, expect, it } from "vitest";
import { isApiError } from "@/lib/api/apiError";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import { listEligibleVisits, listMyReviews, submitReview } from "./myReviewApi";
import { resetMyReviewMockData } from "./myReviewMockData";

async function failure(promise: Promise<unknown>) {
  try {
    await promise;
  } catch (err) {
    if (isApiError(err)) return err;
  }
  throw new Error("Mong đợi ApiError");
}

const GOOD_TEXT = "Bác sĩ khám kỹ và giải thích rõ từng bước.";

beforeEach(() => {
  resetLinkedProfilesMock();
  resetMyReviewMockData();
});

describe("listEligibleVisits", () => {
  it("chỉ lượt đã hoàn tất, chưa đánh giá, của hồ sơ đã gắn; kèm người khám và quan hệ", async () => {
    const { items } = await listEligibleVisits();
    expect(items.map((visit) => visit.visitId)).toEqual(["vis-an-done"]);
    expect(items[0]).toMatchObject({
      serviceName: "Khám chỉnh hình tổng quát",
      patient: { fullName: "Nguyễn Văn An", relationship: "SELF" },
    });
  });

  it("hồ sơ đã bỏ gắn thì lượt của người đó biến mất khỏi danh sách (BR-19)", async () => {
    resetLinkedProfilesMock({ variant: "selfOnly" });
    const { items } = await listEligibleVisits();
    expect(items.every((visit) => visit.patient.relationship === "SELF")).toBe(
      true,
    );
  });

  it("tài khoản không có hồ sơ đã gắn thì danh sách rỗng", async () => {
    resetLinkedProfilesMock({ variant: "empty" });
    await expect(listEligibleVisits()).resolves.toEqual({ items: [] });
  });

  it("failList trả 500 MSG-04", async () => {
    resetMyReviewMockData({ failList: true });
    const err = await failure(listEligibleVisits());
    expect([err.status, err.code]).toEqual([500, "MSG-04"]);
  });
});

describe("listMyReviews", () => {
  it("đánh giá đã gửi mới nhất trước, có trạng thái, phản hồi và người khám", async () => {
    const { items } = await listMyReviews();
    expect(items.map((review) => [review.visitId, review.status])).toEqual([
      ["vis-lan-prev", "PENDING"],
      ["vis-an-prev", "PUBLISHED"],
    ]);
    expect(items[1].clinicReply).toEqual(expect.any(String));
    expect(items[0]).toMatchObject({
      patient: { fullName: "Nguyễn Thị Lan", relationship: "PARENT" },
      clinicReply: null,
    });
  });

  it("đánh giá của hồ sơ đã bỏ gắn không còn hiện", async () => {
    resetLinkedProfilesMock({ variant: "selfOnly" });
    const { items } = await listMyReviews();
    expect(items.map((review) => review.visitId)).toEqual(["vis-an-prev"]);
  });
});

describe("submitReview", () => {
  it("gửi thành công: trạng thái chờ duyệt, nội dung cắt khoảng trắng, lượt rời khỏi danh sách có thể đánh giá", async () => {
    const created = await submitReview({
      visitId: "vis-an-done",
      rating: 5,
      text: `  ${GOOD_TEXT}  `,
    });
    expect(created).toMatchObject({
      visitId: "vis-an-done",
      rating: 5,
      text: GOOD_TEXT,
      status: "PENDING",
      clinicReply: null,
      patient: { relationship: "SELF" },
    });
    expect((await listEligibleVisits()).items).toEqual([]);
    expect((await listMyReviews()).items[0].id).toBe(created.id);
  });

  it("gửi lần hai cho cùng lượt: 409 MSG-56", async () => {
    await submitReview({ visitId: "vis-an-done", rating: 4, text: GOOD_TEXT });
    const err = await failure(
      submitReview({ visitId: "vis-an-done", rating: 4, text: GOOD_TEXT }),
    );
    expect([err.status, err.code]).toEqual([409, "MSG-56"]);
  });

  it("lượt chưa hoàn tất: 409 MSG-56", async () => {
    for (const visitId of ["vis-an-today", "vis-lan-incomplete"]) {
      const err = await failure(
        submitReview({ visitId, rating: 3, text: GOOD_TEXT }),
      );
      expect([err.status, err.code]).toEqual([409, "MSG-56"]);
    }
  });

  it("lượt không tồn tại hoặc thuộc hồ sơ đã bỏ gắn: 403 MSG-56", async () => {
    const missing = await failure(
      submitReview({ visitId: "vis-khong-co", rating: 3, text: GOOD_TEXT }),
    );
    expect([missing.status, missing.code]).toEqual([403, "MSG-56"]);

    resetLinkedProfilesMock({ variant: "selfOnly" });
    const foreign = await failure(
      submitReview({ visitId: "vis-lan-prev", rating: 3, text: GOOD_TEXT }),
    );
    expect([foreign.status, foreign.code]).toEqual([403, "MSG-56"]);
  });

  it("điểm ngoài 1–5 hoặc nội dung dưới 10 ký tự: 400 MSG-57 kèm lỗi đúng trường", async () => {
    const err = await failure(
      submitReview({ visitId: "vis-an-done", rating: 0, text: "ngắn quá" }),
    );
    expect([err.status, err.code]).toEqual([400, "MSG-57"]);
    expect(err.fieldErrors).toEqual([
      { field: "rating", code: "MSG-57" },
      { field: "text", code: "MSG-57" },
    ]);
  });

  it("nội dung trên 1000 ký tự: 400 MSG-57 chỉ ở trường text", async () => {
    const err = await failure(
      submitReview({
        visitId: "vis-an-done",
        rating: 2,
        text: "a".repeat(1001),
      }),
    );
    expect(err.fieldErrors).toEqual([{ field: "text", code: "MSG-57" }]);
  });

  it("kiểm tra dữ liệu trước khi kiểm quyền: yêu cầu sai luôn trả 400", async () => {
    const err = await failure(
      submitReview({ visitId: "vis-khong-co", rating: 9, text: GOOD_TEXT }),
    );
    expect([err.status, err.code]).toEqual([400, "MSG-57"]);
  });
});
