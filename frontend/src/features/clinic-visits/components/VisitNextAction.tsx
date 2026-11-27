import { cn } from "@/lib/utils";
import type { NextAction } from "./visitTimelineModel";

// Ô "việc cần làm bây giờ": một việc duy nhất do visitTimelineModel chọn (được gọi, đang chờ, cần thanh toán...).
// Có số thứ tự thì ô số nằm bên trái, chữ số đều nhau. Màu nền chỉ nhấn thêm, nội dung luôn có chữ.

const TONE: Record<NextAction["kind"], string> = {
  called: "bg-success-soft",
  waiting: "bg-secondary",
  warning: "bg-warning-soft",
  info: "bg-muted",
  done: "bg-success-soft",
};

export function VisitNextAction({ action }: { action: NextAction }) {
  return (
    <div
      aria-live="polite"
      className={cn(
        "flex items-stretch gap-4 rounded-xl p-4 sm:gap-5 sm:p-5",
        TONE[action.kind],
      )}
    >
      {action.number !== null ? (
        <div className="flex w-24 shrink-0 flex-col items-center justify-center rounded-lg bg-card px-3 py-4 sm:w-28">
          <p className="text-sm text-muted-foreground">Số thứ tự</p>
          <p className="text-5xl font-semibold tabular-nums">{action.number}</p>
        </div>
      ) : null}
      <div className="flex min-w-0 flex-1 flex-col justify-center gap-1.5">
        <p
          className={cn(
            "text-xl font-semibold break-words",
            (action.kind === "called" || action.kind === "done") &&
              "text-success",
          )}
        >
          {action.title}
        </p>
        {action.detail ? (
          <p className="text-base break-words">{action.detail}</p>
        ) : null}
      </div>
    </div>
  );
}
