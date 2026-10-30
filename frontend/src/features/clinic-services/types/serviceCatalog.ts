import type { PageQuery } from "@/lib/api/pagination";
import type { PublicService } from "./clinicService";
export type ServiceCatalogItem = PublicService;
export type ServiceCatalogDetail = PublicService;
export type ServiceWriteRequest = Omit<PublicService, "id" | "active">;
export type ServiceUpdateRequest = ServiceWriteRequest;
export type ServiceCatalogQuery = PageQuery & {
  q?: string;
  status?: "ACTIVE" | "DISCONTINUED";
};
