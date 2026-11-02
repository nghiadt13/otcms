import type { PageQuery } from "@/lib/api/pagination";
import type { PublicService } from "./clinicService";
export type ServiceType = "EXAMINATION" | "IMAGING" | "LABORATORY";
export type ServiceModality = "XRAY" | "ULTRASOUND" | "CT" | "MRI";
export type LabIndicator = {
  name: string;
  unit: string;
  refMale: { min: number; max: number };
  refFemale: { min: number; max: number };
};
export type ServiceCatalogItem = PublicService & {
  type: ServiceType;
  modality: ServiceModality | null;
  patientNote: string | null;
  indicators: LabIndicator[];
};
export type ServiceCatalogDetail = ServiceCatalogItem;
export type ServiceWriteRequest = Omit<ServiceCatalogDetail, "id" | "active">;
export type ServiceUpdateRequest = ServiceWriteRequest;
export type ServiceCatalogQuery = PageQuery & {
  q?: string;
  status?: "ACTIVE" | "DISCONTINUED";
};
