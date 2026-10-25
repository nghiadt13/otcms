import { beforeEach, describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import type { ActivityLogFilters } from "../types/activityLog";
import { getActivityLog, listActivityLogs, resetActivityLogStore } from "./activityLogApi";
import {
  MOCK_LOG_COUNT,
  MOCK_NOT_FOUND_ID,
  MOCK_SERVER_ERROR_ID,
  mockLogId,
} from "./activityLogMockData";

const wide: ActivityLogFilters = {
  from: addDays(todayIsoDate(), -30),
  to: todayIsoDate(),
  action: null,
  record: "",
  actor: "",
};

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

describe("activityLogApi", () => {
  beforeEach(() => resetActivityLogStore());

  it("trả 134 mục, 7 trang với size=20, mới nhất trước", async () => {
    const first = await listActivityLogs({ ...wide, page: 0, size: 20 });
    expect(first.totalItems).toBe(MOCK_LOG_COUNT);
    expect(first.totalPages).toBe(7);
    expect(first.items).toHaveLength(20);
    const times = first.items.map((i) => i.occurredAt);
    expect([...times].sort().reverse()).toEqual(times);

    const last = await listActivityLogs({ ...wide, page: 6, size: 20 });
    expect(last.items).toHaveLength(MOCK_LOG_COUNT - 120);
  });

  it("lọc actor và record khớp chứa, không phân biệt hoa thường", async () => {
    const byActor = await listActivityLogs({ ...wide, actor: "LETAN" });
    expect(byActor.totalItems).toBeGreaterThan(0);
    expect(byActor.items.every((i) => i.actorUsername.includes("letan"))).toBe(true);

    const byRecord = await listActivityLogs({ ...wide, record: "xn-2026" });
    expect(byRecord.totalItems).toBeGreaterThan(0);
    expect(byRecord.items.every((i) => i.recordLabel.toLowerCase().includes("xn-2026"))).toBe(true);
  });

  it("lọc theo nhóm hành động", async () => {
    const page = await listActivityLogs({ ...wide, action: "RESULT_CORRECTION" });
    expect(page.totalItems).toBeGreaterThanOrEqual(3);
    expect(page.items.every((i) => i.action === "CORRECT_APPROVED_RESULT")).toBe(true);
    expect(page.items.every((i) => i.correctionReason)).toBe(true);
  });

  it("lọc theo khoảng ngày", async () => {
    const today = todayIsoDate();
    const page = await listActivityLogs({ ...wide, from: today, to: today, size: 100 });
    expect(page.items.every((i) => i.occurredAt.startsWith(today))).toBe(true);
    expect(page.totalItems).toBeLessThan(MOCK_LOG_COUNT);
  });

  it("không có kết quả: items rỗng, totalPages 0", async () => {
    const page = await listActivityLogs({ ...wide, actor: "khong.ton.tai" });
    expect(page).toMatchObject({ items: [], totalItems: 0, totalPages: 0 });
  });

  it("from > to: 400 MSG-54 kèm fieldErrors", async () => {
    const error = await expectApiError(
      listActivityLogs({ ...wide, from: "2026-10-20", to: "2026-10-01" }),
      400,
      "MSG-54",
    );
    expect(error.fieldErrors).toEqual([{ field: "from", code: "MSG-54" }]);
  });

  it("record=loi-may-chu: 500 MSG-04", async () => {
    await expectApiError(listActivityLogs({ ...wide, record: "loi-may-chu" }), 500, "MSG-04");
  });

  it("chi tiết: có recordCode, systemNote; id 404/500 ném lỗi đúng mã", async () => {
    const detail = await getActivityLog(mockLogId(24));
    expect(detail.systemNote).toBeTruthy();
    expect(detail.recordCode).toBeTruthy();
    await expectApiError(getActivityLog(MOCK_NOT_FOUND_ID), 404, "MSG-50");
    await expectApiError(getActivityLog(MOCK_SERVER_ERROR_ID), 500, "MSG-04");
    await expectApiError(getActivityLog("khong-co"), 404, "MSG-50");
  });

  it("không mục nào có khóa before/after/payload", async () => {
    const page = await listActivityLogs({ ...wide, size: 100 });
    const detail = await getActivityLog(page.items[0].id);
    for (const item of [...page.items, detail]) {
      expect(item).not.toHaveProperty("before");
      expect(item).not.toHaveProperty("after");
      expect(item).not.toHaveProperty("payload");
    }
    expect(page.items[0]).not.toHaveProperty("systemNote");
  });
});
