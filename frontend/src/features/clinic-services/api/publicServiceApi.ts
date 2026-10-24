import { clone, mockDelay, mockFail } from "@/lib/api/mockServer";
import type { PublicService, PublicServiceQuery } from "../types/clinicService";
import { SERVICE_SEARCH_MAX_LENGTH } from "../schema/serviceListQuery";
import { PUBLIC_SERVICE_SEED, type PublicServiceRecord } from "./publicServiceMockData";

// LỚP API GIẢ cho /api/v1/public/services (không cần đăng nhập). Chữ ký khớp REST contract;
// khi có BE thật, giữ các hàm này và thay thân bằng lời gọi HTTP công khai. Chỉ dịch vụ
// ACTIVE được trả ra (BR-60); dịch vụ đã ngừng coi như không tồn tại (404 MSG-50).

const LATENCY_MS = process.env.NODE_ENV === "test" ? 0 : 200;

/** So khớp không phân biệt hoa thường và dấu: "xet nghiem" khớp "Xét nghiệm". */
const fold = (text: string) =>
  text.toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/đ/g, "d");

function toPublicService(record: PublicServiceRecord): PublicService {
  return {
    id: record.id,
    name: record.name,
    type: record.type,
    price: record.price,
    description: record.description,
    bookingDetails: record.bookingDetails,
  };
}

/** GET /api/v1/public/services?q=&type= — thứ tự hiển thị theo kho dữ liệu. */
export async function listPublicServices(
  query: PublicServiceQuery = {},
  signal?: AbortSignal,
): Promise<{ items: PublicService[] }> {
  await mockDelay(LATENCY_MS, signal);
  if ((query.q?.length ?? 0) > SERVICE_SEARCH_MAX_LENGTH)
    mockFail(400, "MSG-54", undefined, [{ field: "q", code: "MSG-54" }]);
  const needle = fold(query.q?.trim() ?? "");
  const items = PUBLIC_SERVICE_SEED.filter((service) => service.status === "ACTIVE")
    .filter((service) => !query.type || service.type === query.type)
    .filter((service) => !needle || fold(service.name).includes(needle))
    .map(toPublicService);
  return clone({ items });
}

/** GET /api/v1/public/services/{id} — dịch vụ không còn đang cung cấp: 404 MSG-50. */
export async function getPublicService(
  id: string,
  signal?: AbortSignal,
): Promise<PublicService> {
  await mockDelay(LATENCY_MS, signal);
  const found = PUBLIC_SERVICE_SEED.find(
    (service) => service.status === "ACTIVE" && service.id === id,
  );
  if (!found) mockFail(404, "MSG-50");
  return clone(toPublicService(found));
}

/** Dịch vụ nổi bật trên trang chủ: `limit` mục đầu của danh mục công khai. */
export async function listFeaturedServices(limit = 6): Promise<PublicService[]> {
  const { items } = await listPublicServices();
  return items.slice(0, limit);
}
