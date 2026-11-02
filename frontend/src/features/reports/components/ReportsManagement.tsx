"use client";

import { useCallback, useMemo, useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import type { MsgCode } from "@/lib/api/apiError";
import { useReportRun } from "../hooks/useReportRun";
import { useReportSubjects } from "../hooks/useReportSubjects";
import {
  validateReportForm,
  toReportQuery,
  type ReportFormErrors,
  type ReportFormValues,
} from "../schema/reportFieldRules";
import { ReportForm } from "./ReportForm";
import { ReportResultPanel } from "./ReportResultPanel";

type ReportsManagementProps = {
  /** yyyy-MM-dd, điền sẵn kỳ (ví dụ từ link "Xem chi tiết" ở Bảng điều khiển). */
  initialFrom?: string;
  initialTo?: string;
};

export function ReportsManagement({
  initialFrom = "",
  initialTo = "",
}: ReportsManagementProps) {
  const initialValues = useMemo<ReportFormValues>(
    () => ({ subject: "", from: initialFrom, to: initialTo, breakdown: "" }),
    [initialFrom, initialTo],
  );
  const [values, setValues] = useState<ReportFormValues>(initialValues);
  const [errors, setErrors] = useState<ReportFormErrors>({});
  const { subjects, loading, error, reload } = useReportSubjects();
  const { result, running, exporting, generate, reset, exportFile } =
    useReportRun();

  const handleChange = useCallback((patch: Partial<ReportFormValues>) => {
    setValues((prev) => ({ ...prev, ...patch }));
    setErrors((prev) => {
      const next = { ...prev };
      for (const key of Object.keys(patch)) {
        delete next[key as keyof ReportFormErrors];
        // Ô ngày dùng chung một thông báo lỗi.
        if (key === "from" || key === "to") {
          delete next.from;
          delete next.to;
        }
      }
      return next;
    });
  }, []);

  const handleSubmit = async () => {
    const found = validateReportForm(values, subjects);
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    const outcome = await generate(toReportQuery(values, subjects));
    if (outcome.fieldErrors) {
      const next: ReportFormErrors = {};
      for (const field of ["subject", "from", "to"] as const) {
        const code: MsgCode | undefined = outcome.fieldErrors[field];
        if (code) next[field] = code;
      }
      setErrors(next);
    }
    if (outcome.forbidden) void reload();
  };

  const handleReset = () => {
    reset();
    setValues(initialValues);
    setErrors({});
  };

  return (
    <div>
      <PageHeader
        title="Báo cáo"
        description="Tạo báo cáo theo chủ đề và khoảng ngày; xem trên màn hình hoặc xuất file."
      />
      <ReportForm
        values={values}
        errors={errors}
        subjects={subjects}
        subjectsLoading={loading}
        subjectsError={error}
        running={running}
        onChange={handleChange}
        onSubmit={() => void handleSubmit()}
        onReset={handleReset}
        onRetrySubjects={() => void reload()}
      />
      <ReportResultPanel
        result={result}
        running={running}
        exporting={exporting}
        subjects={subjects}
        onExport={(format) => void exportFile(format)}
      />
    </div>
  );
}
