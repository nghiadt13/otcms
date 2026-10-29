"use client";

import type { InputHTMLAttributes } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

export type ProfileTextFieldProps = {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  error?: string;
  hint?: string;
  disabled: boolean;
  type?: string;
  inputMode?: InputHTMLAttributes<HTMLInputElement>["inputMode"];
  placeholder?: string;
  autoComplete?: string;
  /** Thêm dấu * sau nhãn và `aria-required`; ô tùy chọn không truyền. */
  required?: boolean;
  /** Chỉ đọc nhưng vẫn rõ chữ (khác `disabled` bị mờ): dùng cho ô BE không cho sửa. */
  readOnly?: boolean;
};

// Ô nhập một dòng của khu bệnh nhân: nhãn, ô cao 48px chữ 16px, gợi ý và lỗi gắn bằng
// aria-describedby. Lỗi có role="alert" để đọc ngay khi hiện.
export function ProfileTextField({
  id,
  label,
  value,
  onChange,
  error,
  hint,
  disabled,
  type,
  inputMode,
  placeholder,
  autoComplete,
  required,
  readOnly,
}: ProfileTextFieldProps) {
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy =
    [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(" ") ||
    undefined;

  return (
    <div className="grid gap-2">
      <Label className="text-base" htmlFor={id}>
        {label}
        {required ? (
          <span aria-hidden className="text-destructive">
            {" "}
            *
          </span>
        ) : null}
      </Label>
      <Input
        aria-describedby={describedBy}
        aria-invalid={error ? true : undefined}
        aria-required={required ? true : undefined}
        autoComplete={autoComplete}
        className={cn("h-12 text-base", readOnly && "bg-muted/60")}
        disabled={disabled}
        id={id}
        inputMode={inputMode}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        readOnly={readOnly}
        type={type ?? "text"}
        value={value}
      />
      {hint ? (
        <p className="text-sm text-muted-foreground" id={hintId}>
          {hint}
        </p>
      ) : null}
      {error ? (
        <p className="text-sm text-destructive" id={errorId} role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
