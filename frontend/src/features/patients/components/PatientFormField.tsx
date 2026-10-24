"use client";

import { useId, type ComponentProps } from "react";
import { FieldError } from "@/components/admin/FieldError";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { formatDateOfBirthInput } from "../schema/patientFieldRules";

// Ô nhập có nhãn, gợi ý và lỗi cho form hồ sơ bệnh nhân. Nhãn, mô tả và lỗi đều được
// nối bằng aria-describedby để trình đọc màn hình đọc đủ khi focus vào ô.

export type PatientTextFieldProps = ComponentProps<"input"> & {
  label: string;
  /** Hiện dấu * sau nhãn (đánh dấu cho người dùng; ẩn với trình đọc màn hình). */
  required?: boolean;
  /** Câu gợi ý dưới ô, luôn hiện. */
  help?: string;
  /** Lỗi của ô; khi có thì ô báo đỏ và hiện dưới ô. */
  error?: string;
};

export function PatientTextField({
  label,
  required = false,
  help,
  error,
  ...input
}: PatientTextFieldProps) {
  const id = useId();
  const helpId = `${id}-help`;
  const errorId = `${id}-error`;
  const describedBy =
    [help ? helpId : null, error ? errorId : null].filter(Boolean).join(" ") ||
    undefined;
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>
        {label}
        {required ? <span aria-hidden="true"> *</span> : null}
      </Label>
      <Input
        id={id}
        aria-invalid={Boolean(error) || undefined}
        aria-required={required || undefined}
        aria-describedby={describedBy}
        {...input}
      />
      {help ? (
        <p id={helpId} className="text-xs text-muted-foreground">
          {help}
        </p>
      ) : null}
      <FieldError id={errorId} message={error} />
    </div>
  );
}

type DateOfBirthFieldProps = Omit<PatientTextFieldProps, "value" | "onChange"> & {
  /** dd/mm/yyyy như đang gõ */
  value: string;
  onChange: (text: string) => void;
};

/** Ngày sinh gõ liền: tự chèn "/" (dd/mm/yyyy), không dùng date picker. */
export function DateOfBirthField({ value, onChange, ...rest }: DateOfBirthFieldProps) {
  return (
    <PatientTextField
      {...rest}
      value={value}
      onChange={(event) => onChange(formatDateOfBirthInput(event.target.value))}
      inputMode="numeric"
      autoComplete="off"
      placeholder="dd/mm/yyyy"
      maxLength={10}
    />
  );
}
