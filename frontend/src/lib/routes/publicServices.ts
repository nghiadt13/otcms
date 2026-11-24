export const publicServiceCategories = [
  {
    type: "EXAMINATION",
    label: "Khám cơ xương khớp",
    href: "/services?type=EXAMINATION",
  },
  {
    type: "IMAGING",
    label: "Chẩn đoán hình ảnh",
    href: "/services?type=IMAGING",
  },
  {
    type: "LABORATORY",
    label: "Xét nghiệm",
    href: "/services?type=LABORATORY",
  },
] as const;

export type PublicServiceCategory =
  (typeof publicServiceCategories)[number]["type"];
