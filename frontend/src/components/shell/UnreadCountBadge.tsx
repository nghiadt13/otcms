import { cn } from "@/lib/utils";

/** Số chưa đọc trên chuông thông báo (9+ khi nhiều hơn 9). Số đã nằm trong aria-label của nút nên ẩn khỏi trình đọc màn hình. */
export function UnreadCountBadge({
  count,
  className,
}: {
  count: number;
  className?: string;
}) {
  if (count <= 0) return null;
  return (
    <span
      aria-hidden
      className={cn(
        "absolute -top-1 -right-1 flex min-w-4 items-center justify-center rounded-full bg-coral px-1 text-[10px] leading-4 font-semibold text-coral-ink tabular-nums ring-2 ring-background",
        className,
      )}
    >
      {count > 9 ? "9+" : count}
    </span>
  );
}
