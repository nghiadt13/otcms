import { ApiError } from "@/lib/api/apiError";
import { publicApiRequest } from "@/lib/api/client";
import { SERVICE_SEARCH_MAX_LENGTH } from "../schema/serviceListQuery";
import type { PublicService, PublicServiceQuery } from "../types/clinicService";

type PublicServiceCatalogResponse = { items: PublicService[] };

/** So khớp tên không phân biệt hoa thường và dấu tiếng Việt. */
const fold = (text: string) =>
  text
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d");

/** GET /api/v1/clinic-services — danh mục giá thật, không yêu cầu đăng nhập. */
export async function listPublicServices(
  query: PublicServiceQuery = {},
  signal?: AbortSignal,
): Promise<PublicServiceCatalogResponse> {
  const q = query.q?.trim() ?? "";
  if (q.length > SERVICE_SEARCH_MAX_LENGTH) {
    throw new ApiError(400, {
      code: "MSG-54",
      fieldErrors: [{ field: "q", code: "MSG-54" }],
    });
  }

  const catalog = await publicApiRequest<PublicServiceCatalogResponse>(
    "/api/v1/clinic-services",
    { signal },
  );
  if (!q) return catalog;

  const needle = fold(q);
  return {
    items: catalog.items.filter((service) =>
      fold(service.name).includes(needle),
    ),
  };
}

/** Endpoint hiện trả danh sách; chi tiết được chọn từ cùng projection công khai. */
export async function getPublicService(
  id: string,
  signal?: AbortSignal,
): Promise<PublicService> {
  const { items } = await listPublicServices({}, signal);
  const service = items.find((item) => item.id === id);
  if (!service) throw new ApiError(404, { code: "MSG-50" });
  return service;
}

/** Dịch vụ nổi bật: lấy `limit` dịch vụ đầu từ danh mục backend. */
export async function listFeaturedServices(
  limit = 6,
): Promise<PublicService[]> {
  const { items } = await listPublicServices();
  return items.slice(0, limit);
}
