import { beforeEach, describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import {
  createServiceCatalog,
  discontinueServiceCatalog,
  getServiceCatalog,
  listServiceCatalog,
  resetServiceCatalogMock,
  updateServiceCatalog,
} from "./serviceCatalogApi";
import type { ServiceWriteRequest } from "../types/serviceCatalog";

const newExam = (name: string): ServiceWriteRequest => ({
  type: "EXAMINATION",
  name,
  description: null,
  price: 100000,
  bookingDetails: { slotMinutes: 30, patientNote: null },
  modality: null,
  indicators: [],
});

async function failure(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (error) {
    return error as ApiError;
  }
  throw new Error("Mong đợi lỗi nhưng gọi thành công.");
}

describe("serviceCatalogApi (mock)", () => {
  beforeEach(resetServiceCatalogMock);

  it("chia 26 bản ghi thành 2 trang", async () => {
    const first = await listServiceCatalog({ page: 0 });
    const second = await listServiceCatalog({ page: 1 });
    expect(first.totalItems).toBe(26);
    expect(first.totalPages).toBe(2);
    expect(first.items).toHaveLength(20);
    expect(second.items).toHaveLength(6);
  });

  it("lọc theo q, type, status và bỏ tham số rỗng", async () => {
    expect((await listServiceCatalog({ q: "ct " })).items.every((i) => /ct/i.test(i.name))).toBe(true);
    const imaging = await listServiceCatalog({ type: "IMAGING" });
    expect(imaging.totalItems).toBe(8);
    const stopped = await listServiceCatalog({ status: "DISCONTINUED" });
    expect(stopped.totalItems).toBe(3);
    expect((await listServiceCatalog({ q: "", type: undefined })).totalItems).toBe(26);
    expect((await listServiceCatalog({ q: "zzz" })).items).toEqual([]);
  });

  it("q chứa 'loi' trả 500 MSG-04", async () => {
    const error = await failure(listServiceCatalog({ q: "loi" }));
    expect(error.status).toBe(500);
    expect(error.code).toBe("MSG-04");
  });

  it("tạo trả bản ghi mới và xuất hiện trong danh sách", async () => {
    const created = await createServiceCatalog(newExam("  Khám phục hồi chức năng "));
    expect(created.name).toBe("Khám phục hồi chức năng");
    expect(created.status).toBe("ACTIVE");
    expect((await listServiceCatalog({ q: "phục hồi" })).totalItems).toBe(1);
  });

  it("tên trùng dịch vụ đang cung cấp trả 409 MSG-66 gắn ô name", async () => {
    const error = await failure(createServiceCatalog(newExam("  khám NỘI tổng quát ")));
    expect(error.status).toBe(409);
    expect(error.fieldErrors).toEqual([{ field: "name", code: "MSG-66" }]);
  });

  it("sửa giữ nguyên loại; dịch vụ đã ngừng trả 409 MSG-50", async () => {
    const { type: _type, ...update } = newExam("Khám nội tổng quát");
    void _type;
    const saved = await updateServiceCatalog("svc-01", { ...update, price: 220000 });
    expect(saved.type).toBe("EXAMINATION");
    expect(saved.price).toBe(220000);
    const error = await failure(updateServiceCatalog("svc-disc-01", update));
    expect(error.code).toBe("MSG-50");
    expect(error.status).toBe(409);
  });

  it("ngừng cung cấp đổi trạng thái; ngừng lần hai trả MSG-50", async () => {
    const stopped = await discontinueServiceCatalog("svc-02");
    expect(stopped.status).toBe("DISCONTINUED");
    expect(stopped.allowedActions).toEqual([]);
    expect((await failure(discontinueServiceCatalog("svc-02"))).code).toBe("MSG-50");
    expect((await failure(discontinueServiceCatalog("svc-disc-01"))).status).toBe(409);
  });

  it("id không tồn tại trả 404 MSG-50", async () => {
    const error = await failure(getServiceCatalog("svc-missing"));
    expect(error.status).toBe(404);
    expect(error.code).toBe("MSG-50");
  });
});
