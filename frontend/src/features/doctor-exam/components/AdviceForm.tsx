"use client";

import { useState } from "react";
import { AdminFormField } from "@/components/admin/AdminFormField";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { getDoctorExamApi } from "../api/doctorExamApi";
import { useAsyncAction } from "../hooks/useAsyncAction";
import type { Advice, AdviceInput } from "../types/doctorExam";
import { FormSection } from "./FormSection";

// Lời dặn sau khám (UC42): chăm sóc tại nhà, vận động, phục hồi, dấu hiệu cần quay lại và đề xuất
// tái khám. Lời dặn gắn với bản ghi khám nên cần có bản ghi khám trước.

const FIELDS: Array<{ key: keyof AdviceInput; label: string; rows: number }> = [
  { key: "homeCareAdvice", label: "Chăm sóc tại nhà", rows: 3 },
  { key: "activityAdvice", label: "Vận động và sinh hoạt", rows: 2 },
  { key: "rehabilitationAdvice", label: "Phục hồi chức năng", rows: 2 },
  { key: "warningSigns", label: "Dấu hiệu cần quay lại khám", rows: 2 },
  { key: "followUpRecommendation", label: "Đề xuất tái khám", rows: 2 },
];

const EMPTY: AdviceInput = {
  homeCareAdvice: "",
  activityAdvice: "",
  rehabilitationAdvice: "",
  warningSigns: "",
  followUpRecommendation: "",
};

export function AdviceForm({
  visitId,
  initial,
  disabled,
  disabledReason,
  onSaved,
}: {
  visitId: string;
  initial: Advice | null;
  disabled: boolean;
  disabledReason?: string;
  onSaved: () => void;
}) {
  const [value, setValue] = useState<AdviceInput>(() => ({
    ...EMPTY,
    ...(initial
      ? {
          homeCareAdvice: initial.homeCareAdvice,
          activityAdvice: initial.activityAdvice,
          rehabilitationAdvice: initial.rehabilitationAdvice,
          warningSigns: initial.warningSigns,
          followUpRecommendation: initial.followUpRecommendation,
        }
      : {}),
  }));
  const { pending, run } = useAsyncAction();
  const empty = FIELDS.every(({ key }) => !value[key].trim());

  async function save() {
    const saved = await run(
      () => getDoctorExamApi().saveAdvice(visitId, value),
      "MSG-44",
    );
    if (saved) onSaved();
  }

  return (
    <FormSection
      title="Hướng điều trị và lời dặn"
      description="Lời dặn được đưa vào phiếu kết quả khi hoàn tất lượt khám."
      disabled={disabled || pending}
    >
      <div className="grid gap-4 lg:grid-cols-2">
        {FIELDS.map(({ key, label, rows }) => (
          <AdminFormField key={key} id={`advice-${key}`} label={label}>
            <Textarea
              id={`advice-${key}`}
              rows={rows}
              value={value[key]}
              onChange={(e) =>
                setValue((current) => ({ ...current, [key]: e.target.value }))
              }
            />
          </AdminFormField>
        ))}
      </div>
      <div className="flex flex-wrap items-center justify-end gap-3">
        {disabledReason ? (
          <p className="mr-auto text-sm text-muted-foreground">
            {disabledReason}
          </p>
        ) : null}
        <Button
          type="button"
          disabled={disabled || pending || empty}
          onClick={() => void save()}
        >
          {pending ? "Đang lưu..." : "Lưu lời dặn"}
        </Button>
      </div>
    </FormSection>
  );
}
