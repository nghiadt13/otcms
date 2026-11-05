import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/**
 * Một khối của form dài: khung viền, tiêu đề hiện ra thành `h2`, bọc trong `fieldset` để khóa
 * cả khối bằng `disabled` (lượt khám đã hoàn tất hoặc đang lưu). `legend` chỉ dành cho trình
 * đọc màn hình vì legend hiện mặc định nằm đè lên viền.
 */
export function FormSection({
  title,
  description,
  disabled,
  className,
  children,
}: {
  title: string;
  description?: string;
  disabled?: boolean;
  className?: string;
  children: ReactNode;
}) {
  return (
    <fieldset
      disabled={disabled}
      className={cn(
        "min-w-0 space-y-4 rounded-lg border bg-card p-5",
        className,
      )}
    >
      <legend className="sr-only">{title}</legend>
      <div aria-hidden>
        <h2 className="text-lg font-semibold">{title}</h2>
        {description ? (
          <p className="mt-1 text-xs text-muted-foreground">{description}</p>
        ) : null}
      </div>
      {children}
    </fieldset>
  );
}
