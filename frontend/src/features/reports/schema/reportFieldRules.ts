import type { MsgCode } from "@/lib/api/apiError";
import type {
  ReportBreakdown,
  ReportQuery,
  ReportSubjectCode,
  ReportSubjectOption,
} from "../types/report";

export interface ReportFormValues {
  subject: ReportSubjectCode | "";
  from: string;
  to: string;
  breakdown: ReportBreakdown | "";
}

export type ReportFormField = "subject" | "from" | "to";
export type ReportFormErrors = Partial<Record<ReportFormField, MsgCode>>;

export const EMPTY_REPORT_FORM: ReportFormValues = {
  subject: "",
  from: "",
  to: "",
  breakdown: "",
};

/** Thiếu chủ đề hoặc ngày, hoặc từ > đến → MSG-72 (spec 13). */
export function validateReportForm(
  values: ReportFormValues,
  subjects: ReportSubjectOption[],
): ReportFormErrors {
  const errors: ReportFormErrors = {};
  if (!values.subject || !subjects.some((s) => s.code === values.subject))
    errors.subject = "MSG-72";
  if (!values.from) errors.from = "MSG-72";
  if (!values.to) errors.to = "MSG-72";
  if (values.from && values.to && values.from > values.to) errors.to = "MSG-72";
  return errors;
}

/** Bỏ `breakdown` khi chủ đề không tách hoặc giá trị không thuộc danh sách của chủ đề. */
export function normalizeBreakdown(
  values: ReportFormValues,
  subjects: ReportSubjectOption[],
): ReportBreakdown | undefined {
  const options =
    subjects.find((s) => s.code === values.subject)?.breakdownOptions ?? [];
  return values.breakdown && options.includes(values.breakdown)
    ? values.breakdown
    : undefined;
}

/** Form đã hợp lệ → body POST. */
export function toReportQuery(
  values: ReportFormValues,
  subjects: ReportSubjectOption[],
): ReportQuery {
  const breakdown = normalizeBreakdown(values, subjects);
  return {
    subject: values.subject as ReportSubjectCode,
    from: values.from,
    to: values.to,
    ...(breakdown ? { breakdown } : {}),
  };
}
