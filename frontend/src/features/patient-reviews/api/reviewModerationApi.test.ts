import { beforeEach, describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import {
  approveModerationReview,
  buildReviewListQuery,
  getModerationReview,
  hideModerationReview,
  listModerationReviews,
  replyModerationReview,
  unhideModerationReview,
} from "./reviewModerationApi";
import { resetReviewModerationMockData, reviewIdOf } from "./reviewModerationMockData";

const none = { status: "", rating: "", replied: "", from: "", to: "" } as const;

async function expectApiError(promise: Promise<unknown>, status: number, code: string) {
  const error = await promise.then(
    () => null,
    (e: unknown) => e,
  );
  expect(error).toBeInstanceOf(ApiError);
  expect((error as ApiError).status).toBe(status);
  expect((error as ApiError).code).toBe(code);
  return error as ApiError;
}

beforeEach(() => resetReviewModerationMockData());

describe("buildReviewListQuery", () => {
  it("bỏ giá trị rỗng, giữ replied=false", () => {
    const qs = buildReviewListQuery({ ...none, status: "PENDING", replied: "false", page: 0 });
    const params = new URLSearchParams(qs);
    expect(params.get("status")).toBe("PENDING");
    expect(params.get("replied")).toBe("false");
    expect(params.has("rating")).toBe(false);
    expect(params.has("from")).toBe(false);
    expect(params.get("sort")).toBe("submittedAt,desc");
  });
});

describe("listModerationReviews", () => {
  it("tab Chờ duyệt có 21 bản ghi chia 2 trang, mới nhất trước", async () => {
    const res = await listModerationReviews({ ...none, status: "PENDING" });
    expect(res.totalItems).toBe(21);
    expect(res.totalPages).toBe(2);
    expect(res.items).toHaveLength(20);
    const dates = res.items.map((r) => r.submittedAt);
    expect([...dates].sort().reverse()).toEqual(dates);
  });

  it("counts không đổi khi lọc theo status, rating, ngày", async () => {
    const all = await listModerationReviews(none);
    expect(all.counts).toEqual({ pending: 21, published: 15, hidden: 10, all: 46 });
    const filtered = await listModerationReviews({
      ...none,
      status: "HIDDEN",
      rating: "2",
      from: "2026-10-01",
      to: "2026-10-19",
    });
    expect(filtered.counts).toEqual(all.counts);
  });

  it("tổ hợp lọc không khớp trả 0 bản ghi", async () => {
    const res = await listModerationReviews({
      ...none,
      status: "HIDDEN",
      rating: "5",
      replied: "true",
    });
    expect(res.items).toHaveLength(0);
    expect(res.totalItems).toBe(0);
  });

  it("lọc theo khoảng ngày gửi gồm hai đầu", async () => {
    const res = await listModerationReviews({ ...none, from: "2026-10-19", to: "2026-10-19" });
    expect(res.items.length).toBeGreaterThan(0);
    expect(res.items.every((r) => r.submittedAt.startsWith("2026-10-19"))).toBe(true);
  });

  it("from > to → 400 MSG-54", async () => {
    await expectApiError(
      listModerationReviews({ ...none, from: "2026-10-19", to: "2026-10-01" }),
      400,
      "MSG-54",
    );
  });
});

describe("quyết định kiểm duyệt", () => {
  it("approve khi PENDING → PUBLISHED, allowedActions đổi", async () => {
    const before = await getModerationReview(reviewIdOf(1));
    expect(before.allowedActions).toContain("approve");
    const after = await approveModerationReview(reviewIdOf(1));
    expect(after.status).toBe("PUBLISHED");
    expect(after.allowedActions).toEqual(["hide", "reply"]);
  });

  it("hide giữ phản hồi và đổi nút sang unhide", async () => {
    const after = await hideModerationReview(reviewIdOf(6));
    expect(after.status).toBe("HIDDEN");
    expect(after.reply).not.toBeNull();
    expect(after.allowedActions).toEqual(["unhide", "editReply"]);
  });

  it("sai trạng thái → 409 MSG-56", async () => {
    await expectApiError(approveModerationReview(reviewIdOf(6)), 409, "MSG-56");
    await expectApiError(hideModerationReview(reviewIdOf(14)), 409, "MSG-56");
    await expectApiError(unhideModerationReview(reviewIdOf(6)), 409, "MSG-56");
  });

  it("NN=09 luôn 409 và không đổi dữ liệu", async () => {
    await expectApiError(approveModerationReview(reviewIdOf(9)), 409, "MSG-56");
    expect((await getModerationReview(reviewIdOf(9))).status).toBe("PENDING");
  });
});

describe("replyModerationReview", () => {
  it("lưu phản hồi, đổi reply → editReply", async () => {
    const after = await replyModerationReview(reviewIdOf(2), { text: "  Cảm ơn bạn.  " });
    expect(after.reply?.text).toBe("Cảm ơn bạn.");
    expect(after.replied).toBe(true);
    expect(after.allowedActions).toContain("editReply");
    expect(after.allowedActions).not.toContain("reply");
  });

  it("rỗng, 1001 ký tự và NN=10 → 400 MSG-57 kèm fieldErrors", async () => {
    for (const [nn, text] of [
      [2, ""],
      [2, "a".repeat(1001)],
      [10, "Hợp lệ"],
    ] as const) {
      const error = await expectApiError(
        replyModerationReview(reviewIdOf(nn), { text }),
        400,
        "MSG-57",
      );
      expect(error.fieldErrors).toEqual([{ field: "text", code: "MSG-57" }]);
    }
  });
});

describe("lỗi theo id", () => {
  it("NN=11 → 404 MSG-50; NN=12 → 500 MSG-04; id lạ → 404", async () => {
    await expectApiError(getModerationReview(reviewIdOf(11)), 404, "MSG-50");
    await expectApiError(getModerationReview(reviewIdOf(12)), 500, "MSG-04");
    await expectApiError(getModerationReview("khong-ton-tai"), 404, "MSG-50");
  });
});
