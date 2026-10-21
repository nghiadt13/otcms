// Một enum cho loại dịch vụ và loại phòng (BR-57: dịch vụ hỗ trợ phải khớp loại phòng).
export type ServiceCategory = "EXAMINATION" | "IMAGING" | "LABORATORY";

export const SERVICE_CATEGORIES: ServiceCategory[] = [
  "EXAMINATION",
  "IMAGING",
  "LABORATORY",
];

export const CATEGORY_LABELS: Record<ServiceCategory, string> = {
  EXAMINATION: "Khám",
  IMAGING: "Chẩn đoán hình ảnh",
  LABORATORY: "Xét nghiệm",
};

export const ROOM_CATEGORY_LABELS: Record<ServiceCategory, string> = {
  EXAMINATION: "Phòng khám",
  IMAGING: "Phòng chẩn đoán hình ảnh",
  LABORATORY: "Phòng xét nghiệm",
};
