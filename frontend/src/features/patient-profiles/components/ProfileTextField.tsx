"use client";

import type { InputHTMLAttributes, ReactNode } from "react";
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
  /** Kiểm tra ngay khi rời ô, không chờ bấm Lưu. */
  onBlur?: () => void;
  maxLength?: number;
  /** Phần cố định đứng trước ô nhập, ví dụ mã vùng +84 của số điện thoại. */
  prefix?: ReactNode;
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
  onBlur,
  maxLength,
  prefix,
}: ProfileTextFieldProps) {
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy =
    [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(" ") ||
    undefined;

  const input = (
    <Input
      aria-describedby={describedBy}
      aria-invalid={error ? true : undefined}
      aria-required={required ? true : undefined}
      autoComplete={autoComplete}
      className={cn(
        "h-12 text-base",
        readOnly && "bg-muted/60",
        prefix &&
          "h-full min-w-0 flex-1 rounded-none border-0 bg-transparent shadow-none focus-visible:ring-0 dark:bg-transparent",
      )}
      disabled={disabled}
      id={id}
      inputMode={inputMode}
      maxLength={maxLength}
      onBlur={onBlur}
      onChange={(event) => onChange(event.target.value)}
      placeholder={placeholder}
      readOnly={readOnly}
      type={type ?? "text"}
      value={value}
    />
  );

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
      {prefix ? (
        <div
          className={cn(
            "flex h-12 items-stretch overflow-hidden rounded-lg border border-input transition-colors focus-within:border-ring focus-within:ring-3 focus-within:ring-ring/50",
            error && "border-destructive",
            readOnly && "bg-muted/60",
          )}
        >
          <span className="flex shrink-0 items-center gap-2 border-r bg-muted/40 px-3 text-base">
            {prefix}
          </span>
          {input}
        </div>
      ) : (
        input
      )}
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
