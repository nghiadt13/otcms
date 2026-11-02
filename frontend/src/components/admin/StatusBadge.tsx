import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export type StatusTone = "success" | "neutral" | "warning" | "danger" | "info";

const toneClass: Record<StatusTone, string> = {
  success: "bg-success-soft text-success",
  neutral: "bg-muted text-muted-foreground",
  warning: "bg-warning-soft text-warning",
  danger: "bg-destructive/10 text-destructive",
  info: "bg-secondary text-secondary-foreground",
};

/** Trạng thái luôn có chữ; màu chỉ nhấn thêm, không mang nghĩa một mình. */
export function StatusBadge({
  tone,
  children,
  className,
}: {
  tone: StatusTone;
  children: ReactNode;
  className?: string;
}) {
  return (
    <span
      className={cn(
        "inline-flex h-5 items-center rounded-4xl px-2 text-xs font-medium whitespace-nowrap",
        toneClass[tone],
        className,
      )}
    >
      {children}
    </span>
  );
}
