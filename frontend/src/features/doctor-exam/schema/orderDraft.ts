import type {
  CatalogService,
  OrderDraftItem,
  ServiceKind,
} from "../types/doctorExam";

// Phiếu chỉ định đang soạn (UC45, BR-41): mỗi dịch vụ cần lý do lâm sàng; chẩn đoán hình ảnh
// cần thêm vùng chụp. Hướng dẫn thêm là tùy chọn.

export type OrderDraft = Record<string, OrderDraftItem>;
export type DraftErrors = Record<
  string,
  { clinicalReason?: string; bodyArea?: string }
>;

export const BODY_AREA_MAX_LENGTH = 255;

export function emptyItem(serviceId: string): OrderDraftItem {
  return { serviceId, clinicalReason: "", bodyArea: "", instructions: "" };
}

export function toggleService(
  draft: OrderDraft,
  serviceId: string,
): OrderDraft {
  if (serviceId in draft) {
    const next = { ...draft };
    delete next[serviceId];
    return next;
  }
  return { ...draft, [serviceId]: emptyItem(serviceId) };
}

export function validateDraft(
  draft: OrderDraft,
  catalog: readonly CatalogService[],
): DraftErrors {
  const kinds = new Map(catalog.map((service) => [service.id, service.kind]));
  const errors: DraftErrors = {};
  for (const item of Object.values(draft)) {
    const problem: DraftErrors[string] = {};
    if (!item.clinicalReason.trim())
      problem.clinicalReason = "Nhập lý do lâm sàng.";
    if (kinds.get(item.serviceId) === "IMAGING" && !item.bodyArea.trim())
      problem.bodyArea = "Nhập vùng cần chụp.";
    else if (item.bodyArea.length > BODY_AREA_MAX_LENGTH)
      problem.bodyArea = `Tối đa ${BODY_AREA_MAX_LENGTH} ký tự.`;
    if (problem.clinicalReason || problem.bodyArea)
      errors[item.serviceId] = problem;
  }
  return errors;
}

export function draftTotal(
  draft: OrderDraft,
  catalog: readonly CatalogService[],
): number {
  const prices = new Map(catalog.map((service) => [service.id, service.price]));
  return Object.keys(draft).reduce((sum, id) => sum + (prices.get(id) ?? 0), 0);
}

export function servicesOfKind(
  catalog: readonly CatalogService[],
  kind: ServiceKind,
  query: string,
): CatalogService[] {
  const needle = query.trim().toLowerCase();
  return catalog.filter(
    (service) =>
      service.kind === kind &&
      (!needle || service.name.toLowerCase().includes(needle)),
  );
}

/** Chỉ gửi các dòng còn trong danh mục, theo đúng thứ tự danh mục. */
export function draftItems(
  draft: OrderDraft,
  catalog: readonly CatalogService[],
): OrderDraftItem[] {
  return catalog
    .filter((service) => service.id in draft)
    .map((service) => ({
      ...draft[service.id],
      clinicalReason: draft[service.id].clinicalReason.trim(),
      bodyArea: draft[service.id].bodyArea.trim(),
      instructions: draft[service.id].instructions.trim(),
    }));
}
