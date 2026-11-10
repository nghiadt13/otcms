import {
  CircleCheck,
  Clock,
  ClipboardList,
  type LucideIcon,
} from "lucide-react";
import { StatusBadge, type StatusTone } from "@/components/admin/StatusBadge";
import { cn } from "@/lib/utils";
import type { ResultCounts } from "./resultVisitPresentation";

// Ba số đầu trang Kết quả khám: toàn bộ lượt, lượt có kết quả, lượt chưa hoàn tất. Chỉ đọc.
const ITEMS: {
  key: keyof ResultCounts;
  badge: string;
  tone: StatusTone;
  label: string;
  icon: LucideIcon;
  iconTone: string;
}[] = [
  {
    key: "total",
    badge: "Toàn bộ",
    tone: "neutral",
    label: "Lượt khám",
    icon: ClipboardList,
    iconTone: "bg-primary/10 text-primary",
  },
  {
    key: "completed",
    badge: "Hoàn tất",
    tone: "success",
    label: "Có kết quả",
    icon: CircleCheck,
    iconTone: "bg-success-soft text-success",
  },
  {
    key: "incomplete",
    badge: "Đang xử lý",
    tone: "warning",
    label: "Chưa hoàn tất",
    icon: Clock,
    iconTone: "bg-warning-soft text-warning",
  },
];

export function ResultSummaryCards({ counts }: { counts: ResultCounts }) {
  return (
    <dl className="grid grid-cols-3 gap-3 sm:gap-4">
      {ITEMS.map(({ key, badge, tone, label, icon: Icon, iconTone }) => (
        <div
          className="flex flex-col gap-3 rounded-xl border bg-card p-4 sm:p-5"
          key={key}
        >
          <div className="flex items-start justify-between gap-2">
            <span
              aria-hidden
              className={cn(
                "flex size-9 shrink-0 items-center justify-center rounded-lg",
                iconTone,
              )}
            >
              <Icon className="size-5" />
            </span>
            <StatusBadge className="h-6 text-xs" tone={tone}>
              {badge}
            </StatusBadge>
          </div>
          <div>
            <dd className="text-2xl font-semibold tabular-nums">
              {String(counts[key]).padStart(2, "0")}
            </dd>
            <dt className="text-sm text-muted-foreground">{label}</dt>
          </div>
        </div>
      ))}
    </dl>
  );
}
