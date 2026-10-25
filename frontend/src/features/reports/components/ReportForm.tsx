import type { FormEvent } from "react";
import { DateRangeFilter } from "@/components/admin/DateRangeFilter";
import { FieldError } from "@/components/admin/FieldError";
import { FilterBar } from "@/components/admin/FilterBar";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { formatAppMessage } from "@/lib/messages/appMessages";
import type {
  ReportFormErrors,
  ReportFormValues,
} from "../schema/reportFieldRules";
import type { ReportBreakdown, ReportSubjectCode, ReportSubjectOption } from "../types/report";
import { BREAKDOWN_LABELS } from "../types/reportLabels";

type ReportFormProps = {
  values: ReportFormValues;
  errors: ReportFormErrors;
  subjects: ReportSubjectOption[];
  subjectsLoading: boolean;
  subjectsError: string | null;
  running: boolean;
  onChange: (patch: Partial<ReportFormValues>) => void;
  onSubmit: () => void;
  onReset: () => void;
  onRetrySubjects: () => void;
};

export function ReportForm({
  values,
  errors,
  subjects,
  subjectsLoading,
  subjectsError,
  running,
  onChange,
  onSubmit,
  onReset,
  onRetrySubjects,
}: ReportFormProps) {
  const selected = subjects.find((s) => s.code === values.subject);
  const breakdownOptions = selected?.breakdownOptions ?? [];
  const noSubjects = !subjectsLoading && !subjectsError && subjects.length === 0;
  const message = (code?: string) =>
    code ? formatAppMessage(code as `MSG-${string}`) : undefined;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    onSubmit();
  };

  return (
    <form onSubmit={submit} noValidate className="mb-6">
      <FilterBar>
        <div className="grid w-full gap-1.5 sm:w-64">
          <Label htmlFor="report-subject">Chủ đề báo cáo</Label>
          {subjectsLoading ? (
            <Skeleton className="h-8 w-full" aria-label="Đang tải chủ đề" />
          ) : (
            <NativeSelect
              id="report-subject"
              className="w-full"
              value={values.subject}
              disabled={noSubjects || Boolean(subjectsError)}
              aria-invalid={errors.subject ? true : undefined}
              onChange={(e) =>
                onChange({
                  subject: e.target.value as ReportSubjectCode | "",
                  breakdown: "",
                })
              }
            >
              <NativeSelectOption value="">
                {noSubjects
                  ? "Chưa có chủ đề báo cáo nào trong phạm vi quyền của bạn."
                  : "Chọn chủ đề"}
              </NativeSelectOption>
              {subjects.map((s) => (
                <NativeSelectOption key={s.code} value={s.code}>
                  {s.label}
                </NativeSelectOption>
              ))}
            </NativeSelect>
          )}
          <FieldError message={subjectsError ?? message(errors.subject)} />
          {subjectsError ? (
            <Button
              type="button"
              variant="link"
              size="sm"
              className="justify-self-start px-0"
              onClick={onRetrySubjects}
            >
              Thử lại
            </Button>
          ) : null}
        </div>

        <DateRangeFilter
          from={values.from}
          to={values.to}
          onChange={(range) => onChange(range)}
          error={message(errors.from ?? errors.to)}
        />

        {breakdownOptions.length > 0 ? (
          <div className="grid w-full gap-1.5 sm:w-48">
            <Label htmlFor="report-breakdown">Tách theo</Label>
            <NativeSelect
              id="report-breakdown"
              className="w-full"
              value={values.breakdown}
              onChange={(e) =>
                onChange({ breakdown: e.target.value as ReportBreakdown | "" })
              }
            >
              <NativeSelectOption value="">Không tách</NativeSelectOption>
              {breakdownOptions.map((b) => (
                <NativeSelectOption key={b} value={b}>
                  {BREAKDOWN_LABELS[b]}
                </NativeSelectOption>
              ))}
            </NativeSelect>
          </div>
        ) : null}
      </FilterBar>

      <div className="flex flex-col gap-2 sm:flex-row">
        <Button type="submit" disabled={running} className="w-full sm:w-auto">
          {running ? "Đang tạo..." : "Tạo báo cáo"}
        </Button>
        <Button
          type="button"
          variant="ghost"
          onClick={onReset}
          className="w-full sm:w-auto"
        >
          Đặt lại
        </Button>
      </div>
    </form>
  );
}
