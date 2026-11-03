import type {
  MedicalHistoryField,
  MedicalHistoryUpdate,
} from "../types/patientProfile";

export const MAX_HISTORY_TEXT_LENGTH = 2000;

export const MEDICAL_HISTORY_SECTIONS: readonly {
  field: MedicalHistoryField;
  label: string;
}[] = [
  { field: "conditions", label: "Bệnh nền" },
  { field: "allergies", label: "Dị ứng" },
  { field: "procedures", label: "Phẫu thuật/thủ thuật" },
  { field: "injuries", label: "Chấn thương" },
  { field: "orthopedicHistory", label: "Tiền sử chỉnh hình" },
];

/** Giống Java String.trim() của KAN-28, giữ Unicode whitespace và nội dung bên trong. */
export function trimHistoryText(text: string): string {
  let start = 0;
  let end = text.length;
  while (start < end && text.charCodeAt(start) <= 32) start++;
  while (end > start && text.charCodeAt(end - 1) <= 32) end--;
  return text.slice(start, end);
}

export function isHistoryTextTooLong(text: string): boolean {
  return trimHistoryText(text).length > MAX_HISTORY_TEXT_LENGTH;
}

/** .length đếm UTF-16 units, không đếm code points hay tự cắt nội dung. */
export function invalidHistoryFields(body: MedicalHistoryUpdate): string[] {
  const invalid: string[] = MEDICAL_HISTORY_SECTIONS.filter(
    ({ field }) =>
      typeof body[field] !== "string" || isHistoryTextTooLong(body[field]),
  ).map(({ field }) => field);
  if (!Number.isSafeInteger(body.version) || body.version < 0)
    invalid.push("version");
  return invalid;
}
