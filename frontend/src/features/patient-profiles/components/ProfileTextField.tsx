"use client";

import type { InputHTMLAttributes } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

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
      </Label>
      <Input
        aria-describedby={describedBy}
        aria-invalid={error ? true : undefined}
        autoComplete={autoComplete}
        className="h-12 text-base"
        disabled={disabled}
        id={id}
        inputMode={inputMode}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
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
