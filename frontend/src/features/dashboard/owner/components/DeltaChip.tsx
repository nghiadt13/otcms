import { ArrowDownRight, ArrowUpRight, Minus } from "lucide-react";
import { cn } from "@/lib/utils";
import { deltaOf, deltaPercentText } from "../lib/ownerAnalyticsFormat";

/**
 * Mức thay đổi so với kỳ trước. Hướng được nói bằng mũi tên, dấu và chữ cho
 * người đọc màn hình, không chỉ bằng màu.
 */
export function DeltaChip({
  value,
  previous,
  className,
}: {
  value: number;
  previous: number | null;
  className?: string;
}) {
  const delta = deltaOf(value, previous);
  if (delta.kind === "none") {
    return (
      <span className={cn("text-xs text-muted-foreground", className)}>
        Chưa có kỳ trước
      </span>
    );
  }
  if (delta.kind === "flat") {
    return (
      <span
        className={cn(
          "inline-flex items-center gap-1 text-xs font-medium text-muted-foreground",
          className,
        )}
      >
        <Minus className="size-3.5" aria-hidden />
        <span className="sr-only">Không đổi so với kỳ trước</span>
        <span aria-hidden>0,0%</span>
      </span>
    );
  }
  const up = delta.kind === "up";
  const Icon = up ? ArrowUpRight : ArrowDownRight;
  return (
    <span
      className={cn(
        "inline-flex items-center gap-0.5 text-xs font-semibold tabular-nums",
        up ? "text-success" : "text-destructive",
        className,
      )}
    >
      <Icon className="size-3.5" aria-hidden />
      <span className="sr-only">{up ? "Tăng" : "Giảm"} </span>
      {deltaPercentText(delta)}
      <span className="sr-only"> so với kỳ trước</span>
    </span>
  );
}
