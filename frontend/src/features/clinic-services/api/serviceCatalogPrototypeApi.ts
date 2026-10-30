import { clone, mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import type { PageResponse } from "@/lib/api/pagination";
import type {
  ServiceCatalogDetail,
  ServiceCatalogItem,
  ServiceCatalogQuery,
  ServiceUpdateRequest,
  ServiceWriteRequest,
} from "../types/serviceCatalogPrototype";
import { buildServiceCatalogSeed } from "./serviceCatalogMockData";

// Lớp API Admin "Dịch vụ và giá". Chữ ký khớp REST contract `/api/v1/admin/services`;
// hiện chạy trên kho mock trong bộ nhớ, ném ApiError đúng hình BE thật.

const LATENCY_MS = process.env.NODE_ENV === "test" ? 0 : 250;

let store: ServiceCatalogDetail[] = buildServiceCatalogSeed();
let nextId = 100;

/** Đặt lại dữ liệu giả (dùng trong test). */
export function resetServiceCatalogMock() {
  store = buildServiceCatalogSeed();
  nextId = 100;
}

const normalizeName = (name: string) => name.trim().toLowerCase();

function toItem(detail: ServiceCatalogDetail): ServiceCatalogItem {
  return {
    id: detail.id,
    name: detail.name,
    type: detail.type,
    modality: detail.modality,
    price: detail.price,
    status: detail.status,
    allowedActions: detail.allowedActions,
  };
}

function findOrFail(id: string): ServiceCatalogDetail {
  const found = store.find((item) => item.id === id);
  if (!found) mockFail(404, "MSG-50");
  return found;
}

/** Tên trùng với dịch vụ đang cung cấp khác → 409 MSG-66 gắn vào ô `name`. */
function assertNameAvailable(name: string, selfId?: string) {
  const wanted = normalizeName(name);
  const clash = store.some(
    (item) =>
      item.id !== selfId &&
      item.status === "ACTIVE" &&
      normalizeName(item.name) === wanted,
  );
  if (clash) {
    mockFail(409, "MSG-66", undefined, [{ field: "name", code: "MSG-66" }]);
  }
}

export async function listServiceCatalog(
  query: ServiceCatalogQuery = {},
  signal?: AbortSignal,
): Promise<PageResponse<ServiceCatalogItem>> {
  await mockDelay(LATENCY_MS, signal);
  const q = query.q?.trim().toLowerCase() ?? "";
  if (q.includes("loi")) mockFail(500, "MSG-04"); // ca lỗi hệ thống để thử trạng thái lỗi
  const filtered = store
    .filter((item) => !q || item.name.toLowerCase().includes(q))
    .filter((item) => !query.type || item.type === query.type)
    .filter((item) => !query.status || item.status === query.status)
    .sort((a, b) => a.name.localeCompare(b.name, "vi"))
    .map(toItem);
  return clone(paginate(filtered, query));
}

export async function getServiceCatalog(
  id: string,
  signal?: AbortSignal,
): Promise<ServiceCatalogDetail> {
  await mockDelay(LATENCY_MS, signal);
  return clone(findOrFail(id));
}

export async function createServiceCatalog(
  body: ServiceWriteRequest,
): Promise<ServiceCatalogDetail> {
  await mockDelay(LATENCY_MS);
  assertNameAvailable(body.name);
  nextId += 1;
  const created: ServiceCatalogDetail = {
    ...clone(body),
    name: body.name.trim(),
    id: `svc-${nextId}`,
    status: "ACTIVE",
    allowedActions: ["edit", "discontinue"],
  };
  store = [...store, created];
  return clone(created);
}

export async function updateServiceCatalog(
  id: string,
  body: ServiceUpdateRequest,
): Promise<ServiceCatalogDetail> {
  await mockDelay(LATENCY_MS);
  const current = findOrFail(id);
  if (current.status === "DISCONTINUED") mockFail(409, "MSG-50");
  assertNameAvailable(body.name, id);
  const updated: ServiceCatalogDetail = {
    ...current,
    ...clone(body),
    name: body.name.trim(),
  };
  store = store.map((item) => (item.id === id ? updated : item));
  return clone(updated);
}

export async function discontinueServiceCatalog(
  id: string,
): Promise<ServiceCatalogDetail> {
  await mockDelay(LATENCY_MS);
  const current = findOrFail(id);
  if (current.status === "DISCONTINUED") mockFail(409, "MSG-50");
  const updated: ServiceCatalogDetail = {
    ...current,
    status: "DISCONTINUED",
    allowedActions: [],
  };
  store = store.map((item) => (item.id === id ? updated : item));
  return clone(updated);
}
