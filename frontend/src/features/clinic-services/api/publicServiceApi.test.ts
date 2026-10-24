import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import {
  getPublicService,
  listFeaturedServices,
  listPublicServices,
} from "./publicServiceApi";

describe("listPublicServices", () => {
  it("chỉ trả dịch vụ đang cung cấp, không lộ trạng thái nội bộ (BR-60)", async () => {
    const { items } = await listPublicServices();
    expect(items).toHaveLength(12);
    expect(items.map((s) => s.id)).not.toContain("svc-film-xray-retired");
    for (const item of items) {
      expect(Object.keys(item).sort()).toEqual(
        ["bookingDetails", "description", "id", "name", "price", "type"].sort(),
      );
    }
  });

  it("lọc theo loại dịch vụ", async () => {
    const { items } = await listPublicServices({ type: "IMAGING" });
    expect(items).toHaveLength(4);
    expect(items.every((s) => s.type === "IMAGING")).toBe(true);
  });

  it("tìm theo tên, không phân biệt hoa thường và dấu", async () => {
    const mri = await listPublicServices({ q: "MRI" });
    expect(mri.items.map((s) => s.name)).toEqual(["Chụp MRI khớp"]);

    const labs = await listPublicServices({ q: "xet nghiem" });
    expect(labs.items).toHaveLength(4);
    expect(labs.items.every((s) => s.type === "LABORATORY")).toBe(true);
  });

  it("tiêu chí tìm kiếm quá 100 ký tự trả 400 MSG-54 gắn vào ô q", async () => {
    await expect(listPublicServices({ q: "a".repeat(101) })).rejects.toMatchObject({
      status: 400,
      code: "MSG-54",
      fieldErrors: [{ field: "q", code: "MSG-54" }],
    });
  });
});

describe("getPublicService", () => {
  it("trả chi tiết kèm thông tin đặt lịch của dịch vụ Khám", async () => {
    const service = await getPublicService("svc-consultation");
    expect(service.type).toBe("EXAMINATION");
    expect(service.bookingDetails?.slotMinutes).toBe(30);
  });

  it("dịch vụ đã ngừng và mã không tồn tại đều trả 404 MSG-50", async () => {
    const retired = getPublicService("svc-film-xray-retired");
    await expect(retired).rejects.toBeInstanceOf(ApiError);
    await expect(retired).rejects.toMatchObject({ status: 404, code: "MSG-50" });
    await expect(getPublicService("khong-co")).rejects.toMatchObject({
      status: 404,
      code: "MSG-50",
    });
  });
});

describe("listFeaturedServices", () => {
  it("lấy đúng số mục đầu của danh mục theo thứ tự hiển thị", async () => {
    const featured = await listFeaturedServices(6);
    expect(featured.map((s) => s.id)).toEqual([
      "svc-consultation",
      "svc-arthroscopy",
      "svc-physiotherapy",
      "svc-injection",
      "svc-xray",
      "svc-mri",
    ]);
  });
});
