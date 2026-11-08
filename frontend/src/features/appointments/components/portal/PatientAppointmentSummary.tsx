import {
  CalendarClock,
  CircleCheck,
  Wallet,
  type LucideIcon,
} from "lucide-react";
import { cn } from "@/lib/utils";
import type { PatientAppointmentSummary as Summary } from "../../hooks/usePatientAppointmentSummary";

// Ba số đầu trang Lịch hẹn: sắp tới, chờ thanh toán, đã qua. Chỉ đọc; chưa có số liệu thì không vẽ.
const ITEMS: {
  key: keyof Summary;
  label: string;
  icon: LucideIcon;
  tone: string;
}[] = [
  {
    key: "upcoming",
    label: "Sắp tới",
    icon: CalendarClock,
    tone: "bg-primary/10 text-primary",
  },
  {
    key: "pendingPayment",
    label: "Chờ thanh toán",
    icon: Wallet,
    tone: "bg-warning-soft text-warning",
  },
  {
    key: "past",
    label: "Đã qua",
    icon: CircleCheck,
    tone: "bg-success-soft text-success",
  },
];

export function PatientAppointmentSummary({
  summary,
}: {
  summary: Summary | null;
}) {
  if (!summary) return null;
  return (
    <dl className="grid grid-cols-3 gap-3 sm:gap-4">
      {ITEMS.map(({ key, label, icon: Icon, tone }) => (
        <div
          className="flex flex-col gap-3 rounded-xl border bg-card p-4 sm:flex-row sm:items-center sm:p-5"
          key={key}
        >
          <span
            aria-hidden
            className={cn(
              "flex size-11 shrink-0 items-center justify-center rounded-full",
              tone,
            )}
          >
            <Icon className="size-5" />
          </span>
          <div>
            <dd className="text-2xl font-semibold tabular-nums">
              {String(summary[key]).padStart(2, "0")}
            </dd>
            <dt className="text-sm text-muted-foreground">{label}</dt>
          </div>
        </div>
      ))}
    </dl>
  );
}
