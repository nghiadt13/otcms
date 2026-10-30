import {
  listServiceCatalog,
  createServiceCatalog,
  updateServiceCatalog,
  discontinueServiceCatalog,
  activateServiceCatalog,
} from "./serviceCatalogApi";
import type {
  ServiceCatalogDetail,
  ServiceWriteRequest,
} from "../types/serviceCatalog";
export type AdminService = ServiceCatalogDetail;
export type AdminServiceInput = ServiceWriteRequest;
export async function listAdminServices(signal?: AbortSignal) {
  const first = await listServiceCatalog({ size: 100 }, signal);
  const rest = await Promise.all(
    Array.from({ length: Math.max(0, first.totalPages - 1) }, (_, i) =>
      listServiceCatalog({ page: i + 1, size: 100 }, signal),
    ),
  );
  return [...first.items, ...rest.flatMap((p) => p.items)];
}
export function saveAdminService(
  id: string | undefined,
  input: AdminServiceInput,
) {
  return id ? updateServiceCatalog(id, input) : createServiceCatalog(input);
}
export async function setAdminServiceActive(
  id: string,
  active: boolean,
): Promise<void> {
  if (active) await activateServiceCatalog(id);
  else await discontinueServiceCatalog(id);
}
export * from "./serviceDoctorAssignmentApi";
