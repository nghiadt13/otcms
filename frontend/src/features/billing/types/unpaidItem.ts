import type { PageQuery, PageResponse } from "@/lib/api/pagination";

/** CONSULTATION_FEE = phí khám; SERVICE_ORDER = chỉ định dịch vụ. */
export type UnpaidItemKind = "CONSULTATION_FEE" | "SERVICE_ORDER";
/** Lịch hẹn (trước check-in) hoặc lượt khám (sau check-in). */
export type UnpaidItemReferenceType = "APPOINTMENT" | "VISIT";
/** Màn chỉ xem nên chỉ có "view". */
export type UnpaidItemAction = "view";

export const UNPAID_ITEM_KIND_LABELS: Record<UnpaidItemKind, string> = {
  CONSULTATION_FEE: "Phí khám",
  SERVICE_ORDER: "Chỉ định",
};

export const UNPAID_ITEM_REFERENCE_LABELS: Record<
  UnpaidItemReferenceType,
  string
> = {
  APPOINTMENT: "Lịch hẹn",
  VISIT: "Lượt khám",
};

export interface UnpaidItemPatient {
  fullName: string;
  phone: string | null;
}

export interface UnpaidItemReference {
  type: UnpaidItemReferenceType;
  /** "LH-2610-0102" hoặc "LK-2610-0042". */
  code: string;
  /** yyyy-MM-dd */
  date: string;
}

/** Một phần tử của `items[]` trong GET /api/v1/admin/unpaid-items. */
export interface UnpaidItem {
  id: string;
  kind: UnpaidItemKind;
  /** "Phí khám" hoặc tên dịch vụ. */
  itemName: string;
  /** null khi CONSULTATION_FEE. */
  serviceId: string | null;
  /** Giá chốt lúc đặt/chỉ định; FE hiển thị nguyên trạng, không tính lại. */
  priceVnd: number;
  /** ISO có offset; thời gian chỉ định. */
  orderedAt: string;
  patient: UnpaidItemPatient;
  reference: UnpaidItemReference;
  allowedActions: UnpaidItemAction[];
}

/** GET /api/v1/admin/unpaid-items/{id}. */
export interface UnpaidItemDetail extends UnpaidItem {
  /** null khi phí khám. */
  orderedByDoctorName: string | null;
}

export interface UnpaidItemSummary {
  /** Tổng toàn kỳ lọc, không chỉ trang hiện tại. */
  totalPriceVnd: number;
}

export type UnpaidItemPage = PageResponse<
  UnpaidItem,
  { summary: UnpaidItemSummary }
>;

export interface UnpaidItemFilter {
  /** yyyy-MM-dd, tính cả ngày. */
  from?: string;
  to?: string;
  /** Tên hoặc SĐT bệnh nhân, đã trim. */
  q?: string;
  serviceId?: string;
  kind?: UnpaidItemKind;
}

export type UnpaidItemListQuery = UnpaidItemFilter & PageQuery;

export interface ServiceOption {
  id: string;
  name: string;
}
