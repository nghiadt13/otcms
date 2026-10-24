"use client";

import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import type { PatientSex } from "../types/patientProfile";
import { SEX_OPTIONS } from "./profileOptions";

// Giới tính: hai lựa chọn lớn cạnh nhau, nhãn bọc quanh nút tròn để cả dòng là vùng chạm.
export function ProfileSexField({
  id,
  value,
  onChange,
  error,
  disabled,
}: {
  id: string;
  value: PatientSex | "";
  onChange: (value: PatientSex) => void;
  error?: string;
  disabled: boolean;
}) {
  const errorId = `${id}-error`;
  return (
    <fieldset className="grid gap-2">
      <legend className="text-base font-medium">Giới tính</legend>
      <RadioGroup
        aria-describedby={error ? errorId : undefined}
        className="grid-cols-2 gap-3"
        disabled={disabled}
        onValueChange={(next) => {
          const option = SEX_OPTIONS.find((item) => item.value === next);
          if (option) onChange(option.value);
        }}
        value={value}
      >
        {SEX_OPTIONS.map((option) => (
          <Label
            className="flex min-h-12 items-center gap-3 rounded-lg border px-3 text-base font-normal"
            key={option.value}
          >
            <RadioGroupItem value={option.value} />
            {option.label}
          </Label>
        ))}
      </RadioGroup>
      {error ? (
        <p className="text-sm text-destructive" id={errorId} role="alert">
          {error}
        </p>
      ) : null}
    </fieldset>
  );
}
