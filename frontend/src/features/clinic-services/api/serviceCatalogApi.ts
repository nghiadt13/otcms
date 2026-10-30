import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import type { PageResponse } from "@/lib/api/pagination";
import type {
  ServiceCatalogDetail,
  ServiceCatalogItem,
  ServiceCatalogQuery,
  ServiceWriteRequest,
  ServiceUpdateRequest,
} from "../types/serviceCatalog";
const root = "/api/v1/admin/clinic-services" as const;
async function catalog(signal?: AbortSignal): Promise<ServiceCatalogItem[]> {
  try {
    return (await apiRequest<{ items: ServiceCatalogItem[] }>(root, { signal }))
      .items;
  } catch (error) {
    // Only a missing endpoint permits the documented KAN-9 fallback. Never hide auth/network failures.
    if (!(error instanceof ApiError) || error.status !== 404) throw error;
    return (
      await apiRequest<{ items: ServiceCatalogItem[] }>(
        "/api/v1/staff/clinic-services",
        { signal },
      )
    ).items;
  }
}
export async function listServiceCatalog(
  query: ServiceCatalogQuery = {},
  signal?: AbortSignal,
): Promise<PageResponse<ServiceCatalogItem>> {
  const items = (await catalog(signal)).filter(
    (s) =>
      s.name
        .toLocaleLowerCase("vi")
        .includes((query.q ?? "").trim().toLocaleLowerCase("vi")) &&
      (!query.status || s.active === (query.status === "ACTIVE")),
  );
  const page = Math.max(0, query.page ?? 0),
    size = Math.max(1, Math.min(100, query.size ?? 20));
  return {
    items: items.slice(page * size, (page + 1) * size),
    page,
    size,
    totalItems: items.length,
    totalPages: Math.ceil(items.length / size),
  };
}
export async function getServiceCatalog(
  id: string,
  signal?: AbortSignal,
): Promise<ServiceCatalogDetail> {
  // KAN-9 has no admin detail endpoint: derive a detail only from the actual backend catalog.
  const service = (await catalog(signal)).find((s) => s.id === id);
  if (!service) throw new ApiError(404, { code: "MSG-50" });
  return service;
}
export function createServiceCatalog(
  body: ServiceWriteRequest,
): Promise<ServiceCatalogDetail> {
  return apiRequest(root, { method: "POST", body: JSON.stringify(body) });
}
export function updateServiceCatalog(
  id: string,
  body: ServiceUpdateRequest,
): Promise<ServiceCatalogDetail> {
  return apiRequest(`${root}/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}
export async function discontinueServiceCatalog(
  id: string,
): Promise<ServiceCatalogDetail> {
  await apiRequest<void>(`${root}/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
  return getServiceCatalog(id);
}
export async function activateServiceCatalog(
  id: string,
): Promise<ServiceCatalogDetail> {
  await apiRequest<void>(`${root}/${encodeURIComponent(id)}/activation`, {
    method: "POST",
  });
  return getServiceCatalog(id);
}
export {
  assignServiceDoctor,
  removeServiceDoctor,
  assessAssignmentRemoval,
} from "./serviceDoctorAssignmentApi";
