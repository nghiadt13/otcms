// Quan hệ giữa tài khoản và hồ sơ bệnh nhân đã gắn (BR-16). Đặt ở lib/domain để khu
// bệnh nhân và các màn lễ tân dùng cùng nhãn.
export type CareRelationship =
  "SELF" | "PARENT" | "CHILD" | "SPOUSE" | "SIBLING";

export const CARE_RELATIONSHIP_LABELS: Record<CareRelationship, string> = {
  SELF: "Bản thân",
  PARENT: "Cha/Mẹ",
  CHILD: "Con",
  SPOUSE: "Vợ/Chồng",
  SIBLING: "Anh/Chị/Em",
};
