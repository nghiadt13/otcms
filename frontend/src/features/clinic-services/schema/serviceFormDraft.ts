import type { ServiceCategory } from "@/lib/domain/clinicCategories";
import type {
  LabIndicator,
  ServiceCatalogDetail,
  ServiceModality,
  ServiceUpdateRequest,
  ServiceWriteRequest,
} from "../types/serviceCatalog";

// Trạng thái ô nhập của form dịch vụ. Mọi ô số là chuỗi; đây không phải contract API.

export type IndicatorDraft = {
  name: string;
  unit: string;
  refMale: { min: string; max: string };
  refFemale: { min: string; max: string };
};

export type ServiceFormDraft = {
  /** Tạo: null cho đến khi chọn; sửa: cố định theo bản ghi. */
  type: ServiceCategory | null;
  name: string;
  description: string;
  price: string;
  bookingDetails: { slotMinutes: string; patientNote: string };
  modality: ServiceModality | null;
  indicators: IndicatorDraft[];
};

export const emptyIndicatorDraft = (): IndicatorDraft => ({
  name: "",
  unit: "",
  refMale: { min: "", max: "" },
  refFemale: { min: "", max: "" },
});

export function emptyServiceDraft(type: ServiceCategory | null = null): ServiceFormDraft {
  return {
    type,
    name: "",
    description: "",
    price: "",
    bookingDetails: { slotMinutes: "", patientNote: "" },
    modality: null,
    indicators: type === "LABORATORY" ? [emptyIndicatorDraft()] : [],
  };
}

const text = (value: number | null | undefined) =>
  value === null || value === undefined ? "" : String(value);

export function serviceDetailToDraft(detail: ServiceCatalogDetail): ServiceFormDraft {
  return {
    type: detail.type,
    name: detail.name,
    description: detail.description ?? "",
    price: String(detail.price),
    bookingDetails: {
      slotMinutes: text(detail.bookingDetails.slotMinutes),
      patientNote: detail.bookingDetails.patientNote ?? "",
    },
    modality: detail.modality,
    indicators: detail.indicators.map((row) => ({
      name: row.name,
      unit: row.unit,
      refMale: { min: text(row.refMale.min), max: text(row.refMale.max) },
      refFemale: { min: text(row.refFemale.min), max: text(row.refFemale.max) },
    })),
  };
}

/** Chấp nhận dấu phẩy thập phân kiểu Việt ("0,5"). Trả NaN nếu không phải số hữu hạn. */
export function parseDecimal(value: string): number {
  const normalized = value.trim().replace(",", ".");
  if (normalized === "" || !/^-?\d+(\.\d+)?$/.test(normalized)) return Number.NaN;
  return Number(normalized);
}

const emptyToNull = (value: string) => (value.trim() === "" ? null : value.trim());

/** Chỉ giữ trường thuộc loại; trường khác gửi null hoặc []. Gọi sau khi validate. */
function buildCommon(draft: ServiceFormDraft): ServiceUpdateRequest {
  const type = draft.type;
  const indicators: LabIndicator[] =
    type === "LABORATORY"
      ? draft.indicators.map((row) => ({
          name: row.name.trim(),
          unit: row.unit.trim(),
          refMale: { min: parseDecimal(row.refMale.min), max: parseDecimal(row.refMale.max) },
          refFemale: { min: parseDecimal(row.refFemale.min), max: parseDecimal(row.refFemale.max) },
        }))
      : [];
  const slot = draft.bookingDetails.slotMinutes.trim();
  return {
    name: draft.name.trim(),
    description: emptyToNull(draft.description),
    price: Number(draft.price.trim()),
    bookingDetails: {
      slotMinutes: type === "EXAMINATION" && slot !== "" ? Number(slot) : null,
      patientNote: emptyToNull(draft.bookingDetails.patientNote),
    },
    modality: type === "IMAGING" ? draft.modality : null,
    indicators,
  };
}

export function serviceDraftToCreateRequest(draft: ServiceFormDraft): ServiceWriteRequest {
  if (!draft.type) throw new Error("Chưa chọn loại dịch vụ.");
  return { type: draft.type, ...buildCommon(draft) };
}

/** PUT không gửi `type`. */
export const serviceDraftToUpdateRequest = buildCommon;
