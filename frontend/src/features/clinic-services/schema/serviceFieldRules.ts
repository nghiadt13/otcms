import type { MsgCode } from "@/lib/api/apiError";
import { parseDecimal, type IndicatorDraft } from "./serviceFormDraft";
export type FieldErrorMap = Record<string, MsgCode>;
export function validateIndicators(rows: IndicatorDraft[]): FieldErrorMap {
  const errors: FieldErrorMap = {};
  if (!rows.length) errors.indicators = "MSG-66";
  rows.forEach((row, index) => {
    const prefix = `indicators[${index}]`;
    if (!row.name.trim()) errors[`${prefix}.name`] = "MSG-66";
    if (!row.unit.trim()) errors[`${prefix}.unit`] = "MSG-66";
    for (const key of ["refMale", "refFemale"] as const) {
      const min = parseDecimal(row[key].min),
        max = parseDecimal(row[key].max);
      if (!Number.isFinite(min)) errors[`${prefix}.${key}.min`] = "MSG-66";
      if (!Number.isFinite(max) || min >= max)
        errors[`${prefix}.${key}.max`] = "MSG-66";
    }
  });
  return errors;
}
