import type {
  MedicalHistoryField,
  MedicalHistoryUpdate,
} from "../types/patientProfile";

// Luật tiền sử: năm mục văn bản, mỗi mục tối đa 2000 ký tự. Dùng chung cho ô nhập (báo lỗi
// ngay) và lớp API giả (kiểm tra thân PUT như BE).

export const MAX_HISTORY_TEXT_LENGTH = 2000;

/** Thứ tự hiển thị của năm mục tiền sử, kèm nhãn tiếng Việt. */
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

export function isHistoryTextTooLong(text: string): boolean {
  return text.length > MAX_HISTORY_TEXT_LENGTH;
}

/** Tên các trường sai trong thân PUT: văn bản quá dài hoặc `version` không phải số nguyên. */
export function invalidHistoryFields(body: MedicalHistoryUpdate): string[] {
  const invalid: string[] = MEDICAL_HISTORY_SECTIONS.filter(({ field }) =>
    isHistoryTextTooLong(body[field]),
  ).map(({ field }) => field);
  if (!Number.isInteger(body.version)) invalid.push("version");
  return invalid;
}
