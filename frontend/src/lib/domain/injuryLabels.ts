// Nhãn tiếng Việt cho bên chấn thương (UC37) và hướng điều trị (UC42, BR-40).
// Mã phải khớp ClinicalExamFieldRules phía BE; nhãn giữ theo các form khám hiện có ("Trái", "Phải", "Hai bên").

export const INJURY_SIDES = [
  { value: "LEFT", label: "Trái" },
  { value: "RIGHT", label: "Phải" },
  { value: "BOTH", label: "Hai bên" },
  { value: "NOT_APPLICABLE", label: "Không áp dụng" },
] as const;
export type InjurySide = (typeof INJURY_SIDES)[number]["value"];

export const TREATMENT_DIRECTIONS = [
  { value: "HOME_CARE", label: "Chăm sóc tại nhà" },
  { value: "MEDICATION", label: "Dùng thuốc" },
  { value: "PHYSICAL_THERAPY", label: "Vật lý trị liệu" },
  {
    value: "PROCEDURE_ELSEWHERE",
    label: "Thủ thuật hoặc phẫu thuật tại cơ sở khác",
  },
] as const;
export type TreatmentDirection = (typeof TREATMENT_DIRECTIONS)[number]["value"];

export function isInjurySide(value: string): value is InjurySide {
  return INJURY_SIDES.some((side) => side.value === value);
}

/** Nhãn bên chấn thương; mã không nhận ra (bản ghi cũ nhập tự do) hiển thị nguyên văn. */
export function injurySideLabel(side: string | null | undefined): string {
  if (!side) return "";
  return INJURY_SIDES.find((item) => item.value === side)?.label ?? side;
}
