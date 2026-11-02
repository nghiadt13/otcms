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

/** Validate calendar dates and the inclusive 90-day reporting period. */
function calendarDay(value: string): number | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  const time = Date.parse(`${value}T00:00:00Z`);
  return Number.isFinite(time) &&
    new Date(time).toISOString().slice(0, 10) === value
    ? time
    : null;
}

export function validateReportForm(
  values: ReportFormValues,
  subjects: ReportSubjectOption[],
): ReportFormErrors {
  const errors: ReportFormErrors = {};
  if (!values.subject || !subjects.some((s) => s.code === values.subject))
    errors.subject = "MSG-72";
  if (!values.from) errors.from = "MSG-72";
  if (!values.to) errors.to = "MSG-72";
  const from = calendarDay(values.from);
  const to = calendarDay(values.to);
  if (from === null) errors.from = "MSG-72";
  if (to === null) errors.to = "MSG-72";
  if (
    from !== null &&
    to !== null &&
    (to < from || (to - from) / 86_400_000 + 1 > 90)
  )
    errors.to = "MSG-72";
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
