import { beforeEach, describe, expect, it } from "vitest";
import type { ApiError } from "@/lib/api/apiError";
import {
  createAnnouncement,
  getAnnouncement,
  getAnnouncementOptions,
  listAnnouncements,
} from "./announcementApi";
import { mockConfig, resetAnnouncementMock } from "./announcementMockData";

const empty = { q: "", audience: "" as const, from: "", to: "" };
const valid = {
  title: "Đổi giờ tiếp nhận",
  body: "Nội dung\nnhiều dòng",
  audience: "PATIENT" as const,
  channels: ["EMAIL" as const],
};

async function rejection(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (e) {
    return e as ApiError;
  }
  throw new Error("Không ném lỗi");
}

beforeEach(() => {
  mockConfig.latencyMs = 0;
  resetAnnouncementMock();
});

describe("listAnnouncements", () => {
  it("nhiều trang, mới nhất trước, không lộ body", async () => {
    const page = await listAnnouncements({ ...empty, page: 0, size: 20 });
    expect(page.totalItems).toBeGreaterThan(20);
    expect(page.totalPages).toBe(2);
    expect(page.items).toHaveLength(20);
    expect(page.items[0]).not.toHaveProperty("body");
    const dates = page.items.map((i) => i.publishedAt);
    expect([...dates].sort().reverse()).toEqual(dates);
  });

  it("lọc theo tiêu đề, nhóm và ngày đăng", async () => {
    const byQ = await listAnnouncements({ ...empty, q: "thứ bảy" });
    expect(byQ.items).toHaveLength(1);
    const byAudience = await listAnnouncements({ ...empty, audience: "PUBLIC" });
    expect(byAudience.items.every((i) => i.audience === "PUBLIC")).toBe(true);
    const byDate = await listAnnouncements({
      ...empty,
      from: "2026-10-15",
      to: "2026-10-15",
    });
    expect(byDate.items.some((i) => i.title.includes("X-quang"))).toBe(true);
  });

  it("kích hoạt lỗi 500 và trang rỗng", async () => {
    const err = await rejection(listAnnouncements({ ...empty, q: "loi-tai" }));
    expect(err.status).toBe(500);
    expect(err.code).toBe("MSG-04");
    const none = await listAnnouncements({ ...empty, q: "khong-co" });
    expect(none.totalItems).toBe(0);
  });

  it("từ > đến thì MSG-54", async () => {
    const err = await rejection(
      listAnnouncements({ ...empty, from: "2026-10-20", to: "2026-10-01" }),
    );
    expect(err.status).toBe(400);
    expect(err.code).toBe("MSG-54");
  });
});

describe("createAnnouncement", () => {
  it("trả bản ghi có allowedActions và xuất hiện đầu danh sách", async () => {
    const created = await createAnnouncement(valid);
    expect(created.allowedActions).toEqual(["view"]);
    expect(created.publishedByName).toBeTruthy();
    const page = await listAnnouncements(empty);
    expect(page.items[0].id).toBe(created.id);
  });

  it("403 MSG-50, 503 MSG-04", async () => {
    const forbidden = await rejection(
      createAnnouncement({ ...valid, title: "Thử lỗi quyền" }),
    );
    expect(forbidden.code).toBe("MSG-50");
    const down = await rejection(
      createAnnouncement({ ...valid, title: "Thử lỗi dịch vụ" }),
    );
    expect(down.status).toBe(503);
    expect(down.code).toBe("MSG-04");
  });

  it("400 có fieldErrors cho body", async () => {
    const err = await rejection(
      createAnnouncement({ ...valid, title: "Thử lỗi nội dung" }),
    );
    expect(err.status).toBe(400);
    expect(err.fieldErrors).toEqual([{ field: "body", code: "MSG-71" }]);
  });

  it("nhóm ngoài options bị từ chối", async () => {
    resetAnnouncementMock({ options: { audiences: ["PATIENT"] } });
    const err = await rejection(
      createAnnouncement({ ...valid, audience: "PUBLIC" }),
    );
    expect(err.fieldErrors?.[0].field).toBe("audience");
  });
});

describe("getAnnouncement / options", () => {
  it("chi tiết có body, id lạ thì 404 MSG-50", async () => {
    const { items } = await listAnnouncements(empty);
    expect((await getAnnouncement(items[0].id)).body).toBeTruthy();
    const err = await rejection(getAnnouncement("khong-ton-tai"));
    expect(err.status).toBe(404);
    expect(err.code).toBe("MSG-50");
  });

  it("options có create", async () => {
    expect((await getAnnouncementOptions()).allowedActions).toContain("create");
  });
});
