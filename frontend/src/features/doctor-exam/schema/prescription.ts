import { ApiError } from "@/lib/api/apiError";
import type { PrescriptionLine } from "../types/doctorExam";

const MAX_LENGTHS: Record<keyof PrescriptionLine, number> = {
  medicine: 255,
  dose: 255,
  frequency: 255,
  usage: 2000,
};

const FIELD_LABELS: Record<keyof PrescriptionLine, string> = {
  medicine: "Tên thuốc",
  dose: "Liều dùng",
  frequency: "Tần suất",
  usage: "Cách dùng",
};

/** Return a user-facing warning for the first prescription field over the API limit. */
export function prescriptionLengthWarning(
  lines: PrescriptionLine[],
): string | undefined {
  for (const [index, line] of lines.entries()) {
    for (const key of Object.keys(MAX_LENGTHS) as Array<keyof PrescriptionLine>) {
      const maximum = MAX_LENGTHS[key];
      if (line[key].length > maximum) {
        return `Dòng thuốc ${index + 1}: ${FIELD_LABELS[key]} không được vượt quá ${maximum} ký tự.`;
      }
    }
  }
  return undefined;
}

/** Ignore wholly empty extra rows; never drop a partially entered medicine. */
export function prescriptionItems(
  lines: PrescriptionLine[],
): PrescriptionLine[] {
  const lengthWarning = prescriptionLengthWarning(lines);
  if (lengthWarning) {
    throw new ApiError(400, { code: "MSG-39", detail: lengthWarning });
  }
  const items = lines
    .map(({ medicine, dose, frequency, usage }) => ({
      medicine: medicine.trim(),
      dose: dose.trim(),
      frequency: frequency.trim(),
      usage: usage.trim(),
    }))
    .filter((line) => Object.values(line).some(Boolean));
  if (
    !items.length ||
    items.some((line) => Object.values(line).some((value) => !value))
  ) {
    throw new ApiError(400, {
      code: "MSG-39",
      detail:
        "Nhập đủ tên thuốc, liều dùng, tần suất và cách dùng cho mỗi dòng thuốc.",
    });
  }
  return items;
}
