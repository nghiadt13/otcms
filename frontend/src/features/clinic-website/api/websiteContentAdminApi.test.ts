import { beforeEach, describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import {
  createWebsiteContent,
  getWebsiteContent,
  listWebsiteContents,
  updateWebsiteContent,
  uploadWebsiteImage,
} from "./websiteContentAdminApi";
import {
  NOT_FOUND_CONTENT_ID,
  resetWebsiteContentStore,
  websiteContentStore,
} from "./websiteContentAdminMockData";

beforeEach(() => resetWebsiteContentStore());

async function failure(promise: Promise<unknown>) {
  try {
    await promise;
  } catch (err) {
    return err as ApiError;
  }
  throw new Error("Đáng lẽ phải ném ApiError");
}

describe("listWebsiteContents", () => {
  it("26 bản ghi chia thành 20 + 6, mới cập nhật nhất lên đầu", async () => {
    const first = await listWebsiteContents({ page: 0 });
    const second = await listWebsiteContents({ page: 1 });
    expect(first.items).toHaveLength(20);
    expect(second.items).toHaveLength(6);
    expect(first.totalItems).toBe(26);
    expect(first.totalPages).toBe(2);
    const stamps = first.items.map((item) => item.updatedAt);
    expect([...stamps].sort().reverse()).toEqual(stamps);
    expect(first.items[0]).not.toHaveProperty("body");
  });

  it("lọc theo loại và tìm tiêu đề không phân biệt hoa thường", async () => {
    const about = await listWebsiteContents({ type: "ABOUT_PAGE" });
    expect(about.items.every((item) => item.type === "ABOUT_PAGE")).toBe(true);
    expect(about.totalItems).toBe(2);
    const found = await listWebsiteContents({ q: "CỘT SỐNG" });
    expect(found.totalItems).toBeGreaterThan(0);
  });

  it("q = loi-dich-vu trả 500 MSG-04", async () => {
    const err = await failure(listWebsiteContents({ q: "loi-dich-vu" }));
    expect([err.status, err.code]).toEqual([500, "MSG-04"]);
  });

  it("kho rỗng trả danh sách rỗng", async () => {
    resetWebsiteContentStore([]);
    expect((await listWebsiteContents()).items).toEqual([]);
  });
});

describe("getWebsiteContent", () => {
  it("trả chi tiết có body; id 404 trả MSG-50", async () => {
    const id = websiteContentStore()[0].id;
    expect((await getWebsiteContent(id)).id).toBe(id);
    const err = await failure(getWebsiteContent(NOT_FOUND_CONTENT_ID));
    expect([err.status, err.code]).toEqual([404, "MSG-50"]);
  });
});

describe("createWebsiteContent / updateWebsiteContent", () => {
  it("POST trả bản ghi có publishedAt và đứng đầu danh sách", async () => {
    const created = await createWebsiteContent({
      type: "ABOUT_PAGE",
      title: "  Trang mới ",
      body: "Nội dung",
    });
    expect(created.title).toBe("Trang mới");
    expect(created.publishedAt).toBe(created.updatedAt);
    expect(websiteContentStore()[0].id).toBe(created.id);
  });

  it("PUT giữ publishedAt và đổi updatedAt", async () => {
    const before = websiteContentStore().find((i) => i.type === "ABOUT_PAGE")!;
    const { publishedAt, updatedAt } = before;
    const updated = await updateWebsiteContent(before.id, { title: "Tên khác", body: "Mới" });
    expect(updated.publishedAt).toBe(publishedAt);
    expect(updated.updatedAt).not.toBe(updatedAt);
    expect(updated.title).toBe("Tên khác");
  });

  it("tiêu đề/nội dung rỗng trả 400 MSG-69 kèm fieldErrors", async () => {
    const err = await failure(
      createWebsiteContent({ type: "ABOUT_PAGE", title: " ", body: "" }),
    );
    expect(err.status).toBe(400);
    expect(err.fieldErrors?.map((f) => f.field)).toEqual(["title", "body"]);
    expect(err.fieldErrors?.every((f) => f.code === "MSG-69")).toBe(true);
  });

  it("thiếu hotline trả lỗi trường body.hotline", async () => {
    const err = await failure(
      createWebsiteContent({
        type: "CLINIC_INFO",
        title: "Liên hệ",
        body: { clinicName: "PK", address: "Địa chỉ", hotline: "", email: "", openingHours: ["T2"] },
      }),
    );
    expect(err.fieldErrors).toEqual([{ field: "body.hotline", code: "MSG-69" }]);
  });

  it("tiêu đề 'Lỗi dịch vụ' trả 500; PUT id 404 trả MSG-50", async () => {
    const serverError = await failure(
      createWebsiteContent({ type: "ABOUT_PAGE", title: "Lỗi dịch vụ", body: "x" }),
    );
    expect([serverError.status, serverError.code]).toEqual([500, "MSG-04"]);
    const notFound = await failure(
      updateWebsiteContent(NOT_FOUND_CONTENT_ID, { title: "a", body: "b" }),
    );
    expect([notFound.status, notFound.code]).toEqual([404, "MSG-50"]);
  });
});

describe("uploadWebsiteImage", () => {
  it("nhận jpeg và trả url", async () => {
    const result = await uploadWebsiteImage(new File(["x"], "a.jpg", { type: "image/jpeg" }));
    expect(result.url).toMatch(/^\/media\/website\/.+\.jpg$/);
  });

  it("tệp không phải ảnh hợp lệ trả 400 với fieldErrors file", async () => {
    const err = await failure(
      uploadWebsiteImage(new File(["x"], "a.svg", { type: "image/svg+xml" })),
    );
    expect(err.status).toBe(400);
    expect(err.fieldErrors).toEqual([{ field: "file", code: "MSG-69" }]);
  });
});
