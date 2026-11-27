import { Check } from "lucide-react";
import { cn } from "@/lib/utils";
import { VISIT_STEP_LABELS } from "./visitPresentation";

// Thanh các chặng của lượt khám: đã qua có dấu tick, chặng hiện tại tô đậm, chặng sau để mờ. Mỗi chặng
// luôn có chữ (không chỉ màu) và chặng hiện tại gắn aria-current="step". `current` bằng số chặng nghĩa
// là đã hoàn tất cả lượt.

export function VisitStepTracker({ current }: { current: number }) {
  return (
    <ol aria-label="Các chặng của lượt khám" className="grid grid-cols-4">
      {VISIT_STEP_LABELS.map((label, index) => {
        const done = index < current;
        const active = index === current;
        return (
          <li
            aria-current={active ? "step" : undefined}
            className="relative flex flex-col items-center gap-2 text-center"
            key={label}
          >
            {index > 0 ? (
              <span
                aria-hidden
                className={cn(
                  "absolute top-4 right-1/2 h-0.5 w-full -translate-y-1/2",
                  index <= current ? "bg-primary" : "bg-border",
                )}
              />
            ) : null}
            <span
              className={cn(
                "relative z-10 flex size-8 items-center justify-center rounded-full text-sm font-semibold tabular-nums",
                done && "bg-primary text-primary-foreground",
                active &&
                  "bg-primary text-primary-foreground ring-4 ring-primary/20",
                !done &&
                  !active &&
                  "border-2 border-border bg-card text-muted-foreground",
              )}
            >
              {done ? <Check aria-hidden className="size-4" /> : index + 1}
            </span>
            <span
              className={cn(
                "text-sm leading-tight",
                active
                  ? "font-semibold text-foreground"
                  : "text-muted-foreground",
                done && "text-foreground",
              )}
            >
              {label}
              {done ? <span className="sr-only"> (đã xong)</span> : null}
            </span>
          </li>
        );
      })}
    </ol>
  );
}
