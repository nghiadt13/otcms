import { beforeEach, describe, expect, it } from "vitest";
import {
  getPublicAnnouncement,
  listPublicAnnouncements,
} from "./publicAnnouncementApi";
import { mockStore, resetAnnouncementMock } from "./announcementMockData";

beforeEach(() => {
  resetAnnouncementMock();
});

describe("listPublicAnnouncements", () => {
  it("chỉ gồm thông báo có nhóm nhận Khách và bệnh nhân (BR-61)", async () => {
    const expected = mockStore.announcements.filter((a) => a.audience === "PUBLIC");
    const page = await listPublicAnnouncements({ page: 0, size: 100 });
    expect(page.totalItems).toBe(expected.length);
    expect(expected.length).toBeGreaterThan(0);
    const ids = new Set(expected.map((a) => a.id));
    expect(page.items.every((item) => ids.has(item.id))).toBe(true);
  });

  it("sắp xếp mới nhất trước và chỉ trả trường công khai", async () => {
    const page = await listPublicAnnouncements({ page: 0, size: 100 });
    const dates = page.items.map((item) => item.publishedAt);
    expect([...dates].sort((a, b) => b.localeCompare(a))).toEqual(dates);
    for (const item of page.items) {
      expect(Object.keys(item).sort()).toEqual(["body", "id", "publishedAt", "title"]);
    }
  });

  it("phân trang theo size", async () => {
    const total = (await listPublicAnnouncements({ page: 0, size: 100 })).totalItems;
    const page = await listPublicAnnouncements({ page: 0, size: 1 });
    expect(page.items).toHaveLength(1);
    expect(page.totalPages).toBe(total);
  });
});

describe("getPublicAnnouncement", () => {
  it("thông báo công khai trả chi tiết", async () => {
    const source = mockStore.announcements.find((a) => a.audience === "PUBLIC")!;
    const detail = await getPublicAnnouncement(source.id);
    expect(detail).toEqual({
      id: source.id,
      title: source.title,
      body: source.body,
      publishedAt: source.publishedAt,
    });
  });

  it("thông báo chỉ dành cho nhân viên hoặc bệnh nhân trả 404 MSG-50", async () => {
    const hidden = mockStore.announcements.find((a) => a.audience !== "PUBLIC")!;
    await expect(getPublicAnnouncement(hidden.id)).rejects.toMatchObject({
      status: 404,
      code: "MSG-50",
    });
  });
});
