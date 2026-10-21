import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

// Tiêu đề một khối trên trang giới thiệu: h2 + một câu mô tả, thao tác phụ (ví dụ
// "Xem tất cả") nằm bên phải từ lg. `id` dùng cho aria-labelledby của <section>.
export function PublicSectionHeading({
  id,
  title,
  description,
  action,
  className,
}: {
  id: string;
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between",
        className,
      )}
    >
      <div className="max-w-2xl">
        <h2
          className="text-3xl font-semibold tracking-tight text-balance xl:text-4xl"
          id={id}
        >
          {title}
        </h2>
        {description && (
          <p className="mt-4 text-lg leading-8 text-pretty text-muted-foreground">
            {description}
          </p>
        )}
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
}
