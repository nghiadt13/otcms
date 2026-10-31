import type { PageQuery } from "@/lib/api/pagination";
import type { ServiceCategory } from "@/lib/domain/clinicCategories";

// Kiểu dữ liệu màn Admin "Dịch vụ và giá". Bản công khai giữ ở `clinicService.ts`.

export type ServiceStatus = "ACTIVE" | "DISCONTINUED";
export type ServiceModality = "XRAY" | "ULTRASOUND" | "CT" | "MRI";
export type ServiceAction = "edit" | "discontinue";

/** BR-58: mỗi chỉ số có khoảng riêng cho nam và nữ; không có cờ nguy kịch. */
export type ReferenceRange = { min: number; max: number };

export type LabIndicator = {
  name: string;
  unit: string;
  refMale: ReferenceRange;
  refFemale: ReferenceRange;
};

/** slotMinutes chỉ dùng cho khám (tối thiểu 10); patientNote dùng cho mọi loại. */
export type BookingDetails = {
  slotMinutes: number | null;
  patientNote: string | null;
};

export type ServiceCatalogItem = {
  id: string;
  name: string;
  type: ServiceCategory;
  modality: ServiceModality | null;
  /** VND nguyên */
  price: number;
  status: ServiceStatus;
  allowedActions: ServiceAction[];
};

export type ServiceCatalogDetail = ServiceCatalogItem & {
  description: string | null;
  bookingDetails: BookingDetails;
  indicators: LabIndicator[];
};

export type ServiceWriteRequest = {
  type: ServiceCategory;
  name: string;
  description: string | null;
  price: number;
  bookingDetails: BookingDetails;
  modality: ServiceModality | null;
  indicators: LabIndicator[];
};

/** Loại không đổi sau khi tạo nên PUT không gửi `type`. */
export type ServiceUpdateRequest = Omit<ServiceWriteRequest, "type">;

export type ServiceCatalogQuery = PageQuery & {
  q?: string;
  type?: ServiceCategory;
  status?: ServiceStatus;
};

export const SERVICE_STATUS_LABELS: Record<ServiceStatus, string> = {
  ACTIVE: "Đang cung cấp",
  DISCONTINUED: "Đã ngừng",
};

export const MODALITY_LABELS: Record<ServiceModality, string> = {
  XRAY: "X-quang",
  ULTRASOUND: "Siêu âm",
  CT: "CT",
  MRI: "MRI",
};

export const SERVICE_MODALITIES: ServiceModality[] = [
  "XRAY",
  "ULTRASOUND",
  "CT",
  "MRI",
];
