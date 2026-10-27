import type { ReactNode } from "react";
import { FieldError } from "@/components/admin/FieldError";
import { Label } from "@/components/ui/label";

type WebsiteContentFieldProps = {
  id: string;
  label: string;
  required?: boolean;
  error?: string;
  hint?: string;
  children: ReactNode;
};

/** Nhãn + ô nhập + gợi ý + lỗi đỏ dưới ô, dùng chung cho các nhóm trường của Sheet soạn. */
export function WebsiteContentField({
  id,
  label,
  required,
  error,
  hint,
  children,
}: WebsiteContentFieldProps) {
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>
        {label}
        {required ? (
          <span aria-hidden className="text-destructive">
            *
          </span>
        ) : null}
      </Label>
      {children}
      {hint && !error ? (
        <p id={`${id}-hint`} className="text-xs text-muted-foreground">
          {hint}
        </p>
      ) : null}
      <FieldError id={`${id}-error`} message={error} />
    </div>
  );
}
