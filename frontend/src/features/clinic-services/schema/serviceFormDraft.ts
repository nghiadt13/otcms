import type { LabIndicator } from "../types/serviceCatalog";
export type IndicatorDraft = {
  name: string;
  unit: string;
  refMale: { min: string; max: string };
  refFemale: { min: string; max: string };
};
export const emptyIndicatorDraft = (): IndicatorDraft => ({
  name: "",
  unit: "",
  refMale: { min: "", max: "" },
  refFemale: { min: "", max: "" },
});
export function indicatorToDraft(row: LabIndicator): IndicatorDraft {
  return {
    name: row.name,
    unit: row.unit,
    refMale: { min: String(row.refMale.min), max: String(row.refMale.max) },
    refFemale: {
      min: String(row.refFemale.min),
      max: String(row.refFemale.max),
    },
  };
}
export function parseDecimal(value: string): number {
  const normalized = value.trim().replace(",", ".");
  return /^-?\d+(\.\d+)?$/.test(normalized) ? Number(normalized) : NaN;
}
export function indicatorsToRequest(rows: IndicatorDraft[]): LabIndicator[] {
  return rows.map((row) => ({
    name: row.name.trim(),
    unit: row.unit.trim(),
    refMale: {
      min: parseDecimal(row.refMale.min),
      max: parseDecimal(row.refMale.max),
    },
    refFemale: {
      min: parseDecimal(row.refFemale.min),
      max: parseDecimal(row.refFemale.max),
    },
  }));
}
