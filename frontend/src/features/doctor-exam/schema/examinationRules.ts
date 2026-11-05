import type {
  Diagnosis,
  Examination,
  ExaminationInput,
} from "../types/doctorExam";
import { ICD_CODE_PATTERN } from "./icd10Catalog";

// Luật kiểm tra phía FE cho UC37 (khớp những gì BE kiểm ở `ClinicalExaminationServiceImpl`):
// lưu nháp không đòi gì; hoàn thành cần lý do, triệu chứng, phát hiện khi khám và đúng
// một chẩn đoán chính hợp lệ (MSG-08 ở BE, MSG-39 cho form).

export type ExaminationField =
  "reasonForVisit" | "symptoms" | "examinationFindings" | "diagnoses";

export const EMPTY_EXAMINATION: ExaminationInput = {
  reasonForVisit: "",
  symptoms: "",
  examinationFindings: "",
  injuryBodySite: "",
  injurySide: "",
  injuryMechanism: "",
  diagnoses: [],
};

export function toExaminationInput(
  examination: Examination | undefined,
): ExaminationInput {
  if (!examination) return EMPTY_EXAMINATION;
  const { status, version, ...input } = examination;
  void status;
  void version;
  return input;
}

export function validateForCompletion(
  input: ExaminationInput,
): Partial<Record<ExaminationField, string>> {
  const errors: Partial<Record<ExaminationField, string>> = {};
  if (!input.reasonForVisit.trim())
    errors.reasonForVisit = "Nhập lý do bệnh nhân đến khám.";
  if (!input.symptoms.trim()) errors.symptoms = "Nhập triệu chứng.";
  if (!input.examinationFindings.trim())
    errors.examinationFindings = "Nhập phát hiện khi khám.";
  const primary = input.diagnoses.filter((d) => d.type === "PRIMARY");
  if (primary.length !== 1) errors.diagnoses = "Chọn đúng một chẩn đoán chính.";
  else if (input.diagnoses.some((d) => !ICD_CODE_PATTERN.test(d.code)))
    errors.diagnoses = "Có mã ICD-10 không hợp lệ.";
  return errors;
}

export function primaryDiagnosis(
  diagnoses: readonly Diagnosis[],
): Diagnosis | undefined {
  return diagnoses.find((d) => d.type === "PRIMARY");
}

/** Đặt mã làm chẩn đoán chính; chẩn đoán chính cũ chuyển thành phụ. */
export function setPrimary(
  diagnoses: readonly Diagnosis[],
  code: string,
): Diagnosis[] {
  const rest = diagnoses
    .filter((d) => d.code !== code)
    .map((d) =>
      d.type === "PRIMARY" ? { ...d, type: "SECONDARY" as const } : d,
    );
  return [{ code, type: "PRIMARY" }, ...rest];
}

export function addSecondary(
  diagnoses: readonly Diagnosis[],
  code: string,
): Diagnosis[] {
  if (diagnoses.some((d) => d.code === code)) return [...diagnoses];
  return [...diagnoses, { code, type: "SECONDARY" }];
}

export function removeDiagnosis(
  diagnoses: readonly Diagnosis[],
  code: string,
): Diagnosis[] {
  return diagnoses.filter((d) => d.code !== code);
}
