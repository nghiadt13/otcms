// Kiểu dữ liệu báo cáo, khớp JSON của /api/v1/admin/reports.
export type ReportSubjectCode =
  | "APPOINTMENTS"
  | "VISITS"
  | "SERVICES"
  | "IMAGING"
  | "PAYMENTS"
  | "PENDING_CANCELLED_ORDERS"
  | "REVIEWS";

export type ReportBreakdown = "DATE" | "RECEPTIONIST" | "METHOD";
/** Chỉ CSV (UTF-8 có BOM, mở được trong Excel). */
export type ReportExportFormat = "csv";
export type ReportColumnKind = "text" | "date" | "count" | "money" | "score";
export type ReportAction = "export";

export interface ReportSubjectOption {
  code: ReportSubjectCode;
  label: string;
  /** Rỗng = chủ đề không tách. */
  breakdownOptions: ReportBreakdown[];
}

export interface ReportQuery {
  subject: ReportSubjectCode;
  from: string;
  to: string;
  breakdown?: ReportBreakdown;
}

export interface ReportColumn {
  key: string;
  label: string;
  kind: ReportColumnKind;
}

/** `null` hiển thị "—". */
export type ReportCell = string | number | null;
export type ReportRow = Record<string, ReportCell>;

export interface ReportSection {
  title: string;
  columns: ReportColumn[];
  rows: ReportRow[];
  /** Do BE tính; FE không cộng lại. */
  totalRow: ReportRow | null;
}

export interface ReportResult {
  /** Kỳ đã tạo: tiêu đề và Xuất dùng đúng giá trị này, không dùng form hiện tại. */
  query: ReportQuery;
  hasData: boolean;
  generatedAt: string;
  sections: ReportSection[];
  allowedActions: ReportAction[];
}
