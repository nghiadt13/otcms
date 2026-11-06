import { ApiError } from "@/lib/api/apiError";
import type { PrescriptionLine } from "../types/doctorExam";

/** Ignore wholly empty extra rows; never drop a partially entered medicine. */
export function prescriptionItems(
  lines: PrescriptionLine[],
): PrescriptionLine[] {
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
      code: "MSG-08",
      detail:
        "Nhập đủ tên thuốc, liều dùng, tần suất và cách dùng cho mỗi dòng thuốc.",
    });
  }
  return items;
}
