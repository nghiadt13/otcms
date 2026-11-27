import { Check, Minus } from "lucide-react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { cn } from "@/lib/utils";
import type { StepState, TimelineStep } from "./visitTimelineModel";

// Lộ trình của lượt khám: một danh sách dọc, mỗi bước một dòng (khám bệnh, từng chỉ định, bác sĩ kết luận).
// Biểu tượng bên trái nói bước đã xong / đang làm / chưa tới / đã hủy; bên phải là số thứ tự khi đang xếp
// hàng. Trạng thái luôn có chữ, không chỉ màu. Bước đang làm gắn aria-current="step".

const ICON_CLASS: Record<StepState, string> = {
  done: "bg-primary text-primary-foreground",
  active: "bg-card text-primary ring-4 ring-primary/20 border-2 border-primary",
  called: "bg-success text-white ring-4 ring-success/25",
  todo: "border-2 border-border bg-card text-muted-foreground",
  cancelled: "bg-muted text-muted-foreground",
};

export function VisitTimeline({ steps }: { steps: TimelineStep[] }) {
  return (
    <ol aria-label="Lộ trình lượt khám">
      {steps.map((step, index) => {
        const isLast = index === steps.length - 1;
        const current = step.state === "active" || step.state === "called";
        const meta = [step.room, step.code ? `Mã ${step.code}` : null]
          .filter(Boolean)
          .join(" · ");
        return (
          <li
            aria-current={current ? "step" : undefined}
            className="relative flex gap-4 pb-6 last:pb-0"
            key={step.key}
          >
            {!isLast ? (
              <span
                aria-hidden
                className="absolute top-8 bottom-0 left-4 w-0.5 -translate-x-1/2 bg-border"
              />
            ) : null}
            <span
              className={cn(
                "relative z-10 flex size-8 shrink-0 items-center justify-center rounded-full",
                ICON_CLASS[step.state],
              )}
            >
              {step.state === "done" ? (
                <Check aria-hidden className="size-4" />
              ) : step.state === "cancelled" ? (
                <Minus aria-hidden className="size-4" />
              ) : step.state === "active" || step.state === "called" ? (
                <span
                  aria-hidden
                  className="size-2.5 rounded-full bg-current"
                />
              ) : null}
            </span>
            <div className="min-w-0 flex-1 space-y-1">
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                <p
                  className={cn(
                    "text-base font-semibold break-words",
                    step.state === "cancelled" &&
                      "font-normal text-muted-foreground line-through",
                  )}
                >
                  {step.title}
                </p>
                {step.flag ? (
                  <StatusBadge
                    className="h-6 px-2.5 text-sm"
                    tone={step.flag.tone}
                  >
                    {step.flag.label}
                  </StatusBadge>
                ) : null}
              </div>
              {meta ? (
                <p className="text-sm break-words text-muted-foreground tabular-nums">
                  {meta}
                </p>
              ) : null}
              {step.text ? (
                <p
                  className={cn(
                    "text-base break-words",
                    step.state === "called" && "font-medium text-success",
                    (step.state === "todo" ||
                      step.state === "done" ||
                      step.state === "cancelled") &&
                      "text-muted-foreground",
                  )}
                >
                  {step.text}
                </p>
              ) : null}
            </div>
            {step.number !== null ? (
              <div
                className={cn(
                  "flex min-w-16 shrink-0 flex-col items-center self-start rounded-lg px-3 py-1.5",
                  step.state === "called" ? "bg-success-soft" : "bg-secondary",
                )}
              >
                <span className="text-xs text-muted-foreground">Số</span>
                <span className="text-2xl leading-none font-semibold tabular-nums">
                  {step.number}
                </span>
              </div>
            ) : null}
          </li>
        );
      })}
    </ol>
  );
}
