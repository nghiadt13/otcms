import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import type { PublicService } from "../types/clinicService";
import {
  getPublicService,
  listFeaturedServices,
  listPublicServices,
} from "./publicServiceApi";

const catalog: PublicService[] = [
  {
    id: "8b66b770-c448-4c2d-a474-4f5e35c12a01",
    name: "Khám chấn thương chỉnh hình",
    description: "Khám với bác sĩ chuyên khoa.",
    price: 250000,
    durationMinutes: 30,
    bufferMinutes: 10,
    onlineBookable: true,
    active: true,
  },
  {
    id: "8b66b770-c448-4c2d-a474-4f5e35c12a02",
    name: "Chụp X-quang khớp gối",
    description: null,
    price: 180000,
    durationMinutes: 15,
    bufferMinutes: 5,
    onlineBookable: true,
    active: true,
  },
  {
    id: "8b66b770-c448-4c2d-a474-4f5e35c12a03",
    name: "Xét nghiệm máu",
    description: "Lấy mẫu theo hướng dẫn.",
    price: 120000,
    durationMinutes: 10,
    bufferMinutes: 5,
    onlineBookable: true,
    active: true,
  },
];

function mockCatalog(items: PublicService[] = catalog) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => ({ items }),
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(() => vi.unstubAllGlobals());

describe("publicServiceApi", () => {
  it("đọc giá và thời lượng từ danh mục thật không cần đăng nhập", async () => {
    const fetchMock = mockCatalog();
    const result = await listPublicServices();

    expect(result.items).toEqual(catalog);
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/clinic-services",
      expect.objectContaining({ cache: "no-store" }),
    );
    const requestOptions = fetchMock.mock.calls[0]?.[1] as RequestInit;
    expect(new Headers(requestOptions.headers).has("Authorization")).toBe(
      false,
    );
  });

  it("lọc theo tên không phân biệt hoa thường và dấu", async () => {
    mockCatalog();
    const result = await listPublicServices({ q: "xet nghiem" });
    expect(result.items.map((service) => service.name)).toEqual([
      "Xét nghiệm máu",
    ]);

    const imaging = await listPublicServices({ q: "x-quang" });
    expect(imaging.items.map((service) => service.name)).toEqual([
      "Chụp X-quang khớp gối",
    ]);
  });

  it("từ chối truy vấn tên dài hơn giới hạn mà không gọi backend", async () => {
    const fetchMock = mockCatalog();
    await expect(
      listPublicServices({ q: "a".repeat(101) }),
    ).rejects.toMatchObject({
      status: 400,
      code: "MSG-54",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("lấy chi tiết từ danh mục và trả 404 khi không tìm thấy mã", async () => {
    mockCatalog();
    await expect(getPublicService(catalog[0].id)).resolves.toEqual(catalog[0]);
    await expect(getPublicService("missing-id")).rejects.toBeInstanceOf(
      ApiError,
    );
    await expect(getPublicService("missing-id")).rejects.toMatchObject({
      status: 404,
      code: "MSG-50",
    });
  });

  it("lấy đúng số dịch vụ nổi bật đầu tiên từ backend", async () => {
    mockCatalog();
    await expect(listFeaturedServices(1)).resolves.toEqual([catalog[0]]);
  });
});
