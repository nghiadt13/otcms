"use client";

import { useState, type ReactNode } from "react";
import { AdminFormField } from "@/components/admin/AdminFormField";
import { FieldError } from "@/components/admin/FieldError";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import { Textarea } from "@/components/ui/textarea";
import { ApiError } from "@/lib/api/apiError";
import { notifyError } from "@/lib/messages/notify";
import { getDoctorExamApi } from "../api/doctorExamApi";
import { useAsyncAction } from "../hooks/useAsyncAction";
import {
  toExaminationInput,
  validateForCompletion,
  type ExaminationField,
} from "../schema/examinationRules";
import type { Examination, ExaminationInput } from "../types/doctorExam";
import { FormSection } from "./FormSection";
import { IcdDiagnosisPicker } from "./IcdDiagnosisPicker";

// Form ghi nhận khám (UC37): lý do, triệu chứng, phát hiện khi khám, chấn thương, chẩn đoán ICD-10.
// Lưu nháp không đòi gì (AF1); "Hoàn thành ghi nhận" cần đủ trường và đúng một chẩn đoán chính.

const SIDES = ["Trái", "Phải", "Hai bên"] as const;

const STATUS_LABEL = {
  DRAFT: ["info", "Bản nháp"],
  COMPLETED: ["success", "Đã hoàn thành"],
} as const;

export function ExaminationForm({
  visitId,
  initial,
  locked,
  aside,
  onSaved,
}: {
  visitId: string;
  initial: Examination;
  /** Lượt khám đã hoàn tất: chỉ xem (sửa kết quả đi qua tab Hoàn tất). */
  locked: boolean;
  /** Khối phụ dưới ô chẩn đoán, ví dụ kết quả cận lâm sàng đã có. */
  aside?: ReactNode;
  onSaved: () => void;
}) {
  const [input, setInput] = useState<ExaminationInput>(
    toExaminationInput(initial),
  );
  const [errors, setErrors] = useState<
    Partial<Record<ExaminationField, string>>
  >({});
  const { pending, run } = useAsyncAction();
  const set = <K extends keyof ExaminationInput>(
    key: K,
    value: ExaminationInput[K],
  ) => {
    setInput((current) => ({ ...current, [key]: value }));
    if (key in errors)
      setErrors((current) => ({ ...current, [key]: undefined }));
  };

  async function save(mode: "draft" | "complete") {
    if (mode === "complete") {
      const found = validateForCompletion(input);
      setErrors(found);
      if (Object.keys(found).length) {
        notifyError(new ApiError(400, { code: "MSG-39" }));
        return;
      }
    }
    const saved = await run(
      () => getDoctorExamApi().saveExamination(visitId, input, mode),
      "MSG-44",
    );
    if (saved) onSaved();
  }

  const status = initial.status && STATUS_LABEL[initial.status];
  const readOnly = locked || pending;

  return (
    <form
      className="space-y-6"
      // Chỉ nút "Hoàn thành" mới hoàn thành khám; Enter trong ô một dòng không được kích hoạt nó.
      onSubmit={(event) => event.preventDefault()}
    >
      <div className="grid gap-6 xl:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <div className="space-y-6">
          <FormSection disabled={readOnly} title="Lý do và triệu chứng">
            <AdminFormField id="reason" label="Lý do bệnh nhân đến khám">
              <Textarea
                id="reason"
                rows={2}
                value={input.reasonForVisit}
                aria-invalid={!!errors.reasonForVisit}
                onChange={(e) => set("reasonForVisit", e.target.value)}
              />
              <FieldError message={errors.reasonForVisit} />
            </AdminFormField>
            <AdminFormField id="symptoms" label="Triệu chứng">
              <Textarea
                id="symptoms"
                rows={3}
                value={input.symptoms}
                aria-invalid={!!errors.symptoms}
                onChange={(e) => set("symptoms", e.target.value)}
              />
              <FieldError message={errors.symptoms} />
            </AdminFormField>
          </FormSection>

          <FormSection disabled={readOnly} title="Khám lâm sàng">
            <AdminFormField id="findings" label="Phát hiện khi khám">
              <Textarea
                id="findings"
                rows={4}
                value={input.examinationFindings}
                aria-invalid={!!errors.examinationFindings}
                onChange={(e) => set("examinationFindings", e.target.value)}
              />
              <FieldError message={errors.examinationFindings} />
            </AdminFormField>
            <div className="grid gap-4 sm:grid-cols-3">
              <AdminFormField id="site" label="Vị trí chấn thương">
                <Input
                  id="site"
                  maxLength={120}
                  value={input.injuryBodySite}
                  onChange={(e) => set("injuryBodySite", e.target.value)}
                />
              </AdminFormField>
              <AdminFormField id="side" label="Bên">
                <NativeSelect
                  id="side"
                  className="w-full"
                  value={input.injurySide}
                  onChange={(e) => set("injurySide", e.target.value)}
                >
                  <NativeSelectOption value="">Không ghi</NativeSelectOption>
                  {SIDES.map((side) => (
                    <NativeSelectOption key={side} value={side}>
                      {side}
                    </NativeSelectOption>
                  ))}
                </NativeSelect>
              </AdminFormField>
              <AdminFormField id="mechanism" label="Cơ chế chấn thương">
                <Input
                  id="mechanism"
                  value={input.injuryMechanism}
                  onChange={(e) => set("injuryMechanism", e.target.value)}
                />
              </AdminFormField>
            </div>
          </FormSection>
        </div>

        <div className="space-y-6">
          <FormSection
            disabled={readOnly}
            title="Chẩn đoán ICD-10"
            description="Danh mục nhóm M (cơ xương khớp) và S (chấn thương)."
          >
            <IcdDiagnosisPicker
              value={input.diagnoses}
              onChange={(next) => set("diagnoses", next)}
              error={errors.diagnoses}
              disabled={readOnly}
            />
          </FormSection>
          {aside}
        </div>
      </div>

      <div className="sticky bottom-0 -mx-4 flex flex-wrap items-center gap-3 border-t bg-background/95 px-4 py-3 backdrop-blur md:-mx-6 md:px-6">
        {status ? (
          <StatusBadge tone={status[0]}>{status[1]}</StatusBadge>
        ) : (
          <StatusBadge tone="neutral">Chưa ghi nhận</StatusBadge>
        )}
        {locked ? (
          <p className="text-sm text-muted-foreground">
            Lượt khám đã hoàn tất. Sửa kết quả ở tab Hoàn tất lượt khám.
          </p>
        ) : null}
        <div className="ml-auto flex gap-2">
          <Button
            type="button"
            variant="outline"
            // BE đặt lại trạng thái DRAFT ở mọi lần lưu, nên với bản đã hoàn thành chỉ cho hoàn thành lại.
            disabled={readOnly || initial.status === "COMPLETED"}
            title={
              initial.status === "COMPLETED"
                ? "Bản đã hoàn thành: sửa xong bấm Hoàn thành ghi nhận khám"
                : undefined
            }
            onClick={() => void save("draft")}
          >
            Lưu nháp
          </Button>
          <Button
            type="button"
            disabled={readOnly}
            onClick={() => void save("complete")}
          >
            {pending ? "Đang lưu..." : "Hoàn thành ghi nhận khám"}
          </Button>
        </div>
      </div>
    </form>
  );
}
