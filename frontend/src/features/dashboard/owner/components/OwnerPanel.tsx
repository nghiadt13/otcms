import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

type OwnerPanelProps = {
  title: string;
  description?: string;
  /** Điều khiển gắn với khối, ví dụ nút "Xem dạng bảng". */
  actions?: ReactNode;
  className?: string;
  children: ReactNode;
};

/** Khung một khối của dashboard: tiêu đề, mô tả ngắn, điều khiển rồi nội dung. Không lồng khung trong khung. */
export function OwnerPanel({
  title,
  description,
  actions,
  className,
  children,
}: OwnerPanelProps) {
  return (
    <section className={cn("rounded-xl border bg-card", className)}>
      <header className="flex flex-wrap items-start justify-between gap-3 px-5 pt-5">
        <div>
          <h2 className="text-base font-semibold tracking-tight">{title}</h2>
          {description ? (
            <p className="mt-0.5 text-sm text-muted-foreground">
              {description}
            </p>
          ) : null}
        </div>
        {actions}
      </header>
      <div className="px-5 pb-5 pt-4">{children}</div>
    </section>
  );
}
