"use client";

import { Eye, EyeOff } from "lucide-react";
import { useId, useState, type ReactNode } from "react";
import { FieldError } from "@/components/admin/FieldError";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

type PasswordInputFieldProps = {
  label: string;
  value: string;
  onChange: (value: string) => void;
  onBlur?: () => void;
  autoComplete: "current-password" | "new-password";
  error?: string | null;
  disabled?: boolean;
  /** Gợi ý dưới ô, ví dụ checklist tiêu chí mật khẩu. */
  hint?: ReactNode;
};

/** Ô mật khẩu có nút hiện/ẩn và lỗi đỏ ngay dưới ô. */
export function PasswordInputField({
  label,
  value,
  onChange,
  onBlur,
  autoComplete,
  error,
  disabled,
  hint,
}: PasswordInputFieldProps) {
  const id = useId();
  const errorId = `${id}-error`;
  const [visible, setVisible] = useState(false);
  const Icon = visible ? EyeOff : Eye;

  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <div className="flex items-center gap-1">
        <Input
          id={id}
          type={visible ? "text" : "password"}
          value={value}
          autoComplete={autoComplete}
          disabled={disabled}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : undefined}
          onChange={(event) => onChange(event.target.value)}
          onBlur={onBlur}
        />
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={visible ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
          aria-pressed={visible}
          onClick={() => setVisible((current) => !current)}
        >
          <Icon aria-hidden />
        </Button>
      </div>
      <FieldError id={errorId} message={error} />
      {hint}
    </div>
  );
}
