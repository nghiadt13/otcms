import type { ReportBreakdown, ReportExportFormat } from "./report";

export const BREAKDOWN_LABELS: Record<ReportBreakdown, string> = {
  DATE: "Ngày",
  RECEPTIONIST: "Lễ tân",
  METHOD: "Phương thức",
};

export const EXPORT_FORMAT_LABELS: Record<ReportExportFormat, string> = {
  csv: "CSV (mở bằng Excel)",
};
