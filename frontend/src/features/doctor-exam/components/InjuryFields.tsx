"use client";

import { AdminFormField } from "@/components/admin/AdminFormField";
import { FieldError } from "@/components/admin/FieldError";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import { Textarea } from "@/components/ui/textarea";
import {
  INJURY_SIDES,
  TREATMENT_DIRECTIONS,
  type TreatmentDirection,
} from "@/lib/domain/injuryLabels";
import type { ExaminationField } from "../schema/examinationRules";
import type { ExaminationInput } from "../types/doctorExam";

// Chấn thương có cấu trúc (UC37: vị trí, bên, cơ chế, mô tả) và hướng điều trị (UC42, BR-40).
// Dùng chung cho form khám và hộp thoại sửa kết quả; khi hoàn thành mọi trường đều bắt buộc (MSG-39).

export function InjuryFields({
  input,
  errors,
  onSet,
  idPrefix = "",
}: {
  input: ExaminationInput;
  errors: Partial<Record<ExaminationField, string>>;
  onSet: <K extends keyof ExaminationInput>(
    key: K,
    value: ExaminationInput[K],
  ) => void;
  /** Tiền tố id để hai form trên cùng màn hình không trùng id. */
  idPrefix?: string;
}) {
  const directions = input.treatmentDirections ?? [];
  const toggleDirection = (code: TreatmentDirection, checked: boolean) =>
    onSet(
      "treatmentDirections",
      checked
        ? [...directions, code]
        : directions.filter((item) => item !== code),
    );

  return (
    <>
      <div className="grid gap-4 sm:grid-cols-3">
        <AdminFormField id={`${idPrefix}site`} label="Vị trí chấn thương">
          <Input
            id={`${idPrefix}site`}
            maxLength={120}
            value={input.injuryBodySite}
            aria-invalid={!!errors.injuryBodySite}
            onChange={(e) => onSet("injuryBodySite", e.target.value)}
          />
          <FieldError message={errors.injuryBodySite} />
        </AdminFormField>
        <AdminFormField id={`${idPrefix}side`} label="Bên">
          <NativeSelect
            id={`${idPrefix}side`}
            className="w-full"
            value={input.injurySide}
            aria-invalid={!!errors.injurySide}
            onChange={(e) => onSet("injurySide", e.target.value)}
          >
            <NativeSelectOption value="">Không ghi</NativeSelectOption>
            {INJURY_SIDES.map((side) => (
              <NativeSelectOption key={side.value} value={side.value}>
                {side.label}
              </NativeSelectOption>
            ))}
          </NativeSelect>
          <FieldError message={errors.injurySide} />
        </AdminFormField>
        <AdminFormField id={`${idPrefix}mechanism`} label="Cơ chế chấn thương">
          <Input
            id={`${idPrefix}mechanism`}
            maxLength={1000}
            value={input.injuryMechanism}
            aria-invalid={!!errors.injuryMechanism}
            onChange={(e) => onSet("injuryMechanism", e.target.value)}
          />
          <FieldError message={errors.injuryMechanism} />
        </AdminFormField>
      </div>
      <AdminFormField
        id={`${idPrefix}injury-description`}
        label="Mô tả chấn thương"
      >
        <Textarea
          id={`${idPrefix}injury-description`}
          rows={2}
          maxLength={2000}
          value={input.injuryDescription ?? ""}
          aria-invalid={!!errors.injuryDescription}
          onChange={(e) => onSet("injuryDescription", e.target.value)}
        />
        <FieldError message={errors.injuryDescription} />
      </AdminFormField>
      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">Hướng điều trị</legend>
        <div className="grid gap-2 sm:grid-cols-2">
          {TREATMENT_DIRECTIONS.map((direction) => (
            <label
              key={direction.value}
              className="flex items-center gap-2 text-sm"
            >
              <Checkbox
                checked={directions.includes(direction.value)}
                onCheckedChange={(checked) =>
                  toggleDirection(direction.value, checked === true)
                }
              />
              {direction.label}
            </label>
          ))}
        </div>
        <FieldError message={errors.treatmentDirections} />
      </fieldset>
    </>
  );
}
