import { clone, mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import type { PageResponse } from "@/lib/api/pagination";
import type {
  ServiceCatalogDetail,
  ServiceCatalogItem,
  ServiceCatalogQuery,
  ServiceUpdateRequest,
  ServiceWriteRequest,
} from "../types/serviceCatalog";
import { buildServiceCatalogSeed } from "./serviceCatalogMockData";
import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import type { ItemsResponse } from "@/lib/api/pagination";

// Lớp API Admin "Dịch vụ và giá" dùng `/api/v1/admin/clinic-services`;
// ứng dụng gọi backend thật, còn test dùng kho mock cùng kiểu lỗi.

const LATENCY_MS = process.env.NODE_ENV === "test" ? 0 : 250;

let store: ServiceCatalogDetail[] = buildServiceCatalogSeed();
let nextId = 100;

/** Đặt lại dữ liệu giả (dùng trong test). */
export function resetServiceCatalogMock() {
  store = buildServiceCatalogSeed();
  nextId = 100;
}

const normalizeName = (name: string) => name.trim().toLowerCase();
const USE_MOCK = process.env.NODE_ENV === "test";

type BackendService = {
  id: string;
  name: string;
  description: string | null;
  type: ServiceCatalogDetail["type"];
  price: number;
  durationMinutes: number;
  bufferMinutes: number;
  onlineBookable: boolean;
  active: boolean;
  modality: ServiceCatalogDetail["modality"];
  patientNote: string | null;
  indicators: ServiceCatalogDetail["indicators"];
};

function mapBackendService(row: BackendService): ServiceCatalogDetail {
  const active = row.active;
  return {
    id: row.id,
    name: row.name,
    type: row.type,
    modality: row.modality,
    price: Number(row.price),
    status: active ? "ACTIVE" : "DISCONTINUED",
    allowedActions: active ? ["edit", "discontinue"] : [],
    description: row.description,
    bookingDetails: {
      slotMinutes: row.type === "EXAMINATION" ? row.durationMinutes : null,
      patientNote: row.patientNote,
    },
    indicators: row.indicators ?? [],
  };
}

function toBackendBody(body: ServiceWriteRequest | ServiceUpdateRequest) {
  const type = "type" in body ? body.type : undefined;
  return {
    ...(type ? { type } : {}),
    name: body.name,
    description: body.description,
    price: body.price,
    durationMinutes: body.bookingDetails.slotMinutes ?? 30,
    bufferMinutes: 0,
    onlineBookable: type === "EXAMINATION",
    modality: body.modality,
    patientNote: body.bookingDetails.patientNote,
    indicators: body.indicators,
  };
}

async function liveRequest<T>(request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (error) {
    if (!(error instanceof ApiError) || !error.errorCode) throw error;
    switch (error.errorCode) {
      case "SERVICE_NOT_FOUND":
        throw new ApiError(error.status, { code: "MSG-50" });
      case "VALIDATION_FAILED":
        throw new ApiError(error.status, { code: "MSG-66" });
      case "DUPLICATE_SERVICE_NAME":
        throw new ApiError(error.status, {
          fieldErrors: [{ field: "name", code: "MSG-66" }],
        });
      default:
        throw error;
    }
  }
}

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
  if (!USE_MOCK) {
    const response = await liveRequest(
      apiRequest<ItemsResponse<BackendService>>(
        "/api/v1/admin/clinic-services",
        { signal },
      ),
    );
    const q = query.q?.trim().toLocaleLowerCase("vi") ?? "";
    const filtered = response.items
      .map(mapBackendService)
      .filter((item) => !q || item.name.toLocaleLowerCase("vi").includes(q))
      .filter((item) => !query.type || item.type === query.type)
      .filter((item) => !query.status || item.status === query.status)
      .map(toItem);
    return paginate(filtered, query);
  }
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
  if (!USE_MOCK) {
    const response = await liveRequest(
      apiRequest<BackendService>(`/api/v1/admin/clinic-services/${id}`, {
        signal,
      }),
    );
    return mapBackendService(response);
  }
  await mockDelay(LATENCY_MS, signal);
  return clone(findOrFail(id));
}

export async function createServiceCatalog(
  body: ServiceWriteRequest,
): Promise<ServiceCatalogDetail> {
  if (!USE_MOCK) {
    const response = await liveRequest(
      apiRequest<BackendService>("/api/v1/admin/clinic-services", {
        method: "POST",
        body: JSON.stringify(toBackendBody(body)),
      }),
    );
    return mapBackendService(response);
  }
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
  if (!USE_MOCK) {
    const response = await liveRequest(
      apiRequest<BackendService>(`/api/v1/admin/clinic-services/${id}`, {
        method: "PUT",
        body: JSON.stringify(toBackendBody(body)),
      }),
    );
    return mapBackendService(response);
  }
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
  if (!USE_MOCK) {
    await liveRequest(
      apiRequest<void>(`/api/v1/admin/clinic-services/${id}`, {
        method: "DELETE",
      }),
    );
    return getServiceCatalog(id);
  }
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
