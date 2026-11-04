import { cn } from "@/lib/utils";

const STEPS = ["Thông tin", "Quan hệ", "Xác nhận"] as const;

/** Thanh bước của form thêm thành viên; `current` bắt đầu từ 1. */
export function DependentWizardSteps({ current }: { current: 1 | 2 | 3 }) {
  return (
    <ol aria-label="Các bước thêm thành viên" className="flex gap-3">
      {STEPS.map((label, index) => {
        const number = index + 1;
        const active = number === current;
        return (
          <li
            aria-current={active ? "step" : undefined}
            className="flex min-w-0 flex-1 flex-col gap-2"
            key={label}
          >
            <span
              className={cn(
                "h-1.5 rounded-full",
                number <= current ? "bg-primary" : "bg-muted",
              )}
            />
            <span
              className={cn(
                "text-sm",
                active
                  ? "font-semibold text-foreground"
                  : "text-muted-foreground",
              )}
            >
              {number}. {label}
            </span>
          </li>
        );
      })}
    </ol>
  );
}
