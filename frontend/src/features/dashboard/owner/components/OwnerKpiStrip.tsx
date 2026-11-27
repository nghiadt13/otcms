import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatInteger, formatDecimal1 } from "@/lib/format/formatNumber";
import type { OwnerAnalytics } from "../types/ownerAnalytics";
import { sharePercent } from "../lib/ownerAnalyticsFormat";
import { DeltaChip } from "./DeltaChip";

/** Nền gạch chéo cho phần "tiền mặt": hình dạng cộng màu, nên không phụ thuộc riêng vào màu. */
export const CASH_HATCH_STYLE = {
  backgroundColor: "var(--chart-2)",
  backgroundImage:
    "repeating-linear-gradient(135deg, transparent 0 3px, color-mix(in oklab, var(--color-coral-strong) 55%, transparent) 3px 5px)",
} as const;

function RevenueCell({ revenue }: { revenue: OwnerAnalytics["revenue"] }) {
  const { total, payos, cash } = revenue;
  const payosShare = sharePercent(payos.value, total.value);
  return (
    <div className="col-span-2 bg-card p-5 lg:col-span-1">
      <dt className="text-sm text-muted-foreground">Doanh thu</dt>
      <dd className="mt-1 text-3xl font-semibold tracking-tight tabular-nums">
        {formatCurrencyVnd(total.value)}
      </dd>
      <dd className="mt-1 flex flex-wrap items-center gap-x-2 text-xs text-muted-foreground">
        <DeltaChip value={total.value} previous={total.previousValue} />
        {total.previousValue !== null ? (
          <span>so với {formatCurrencyVnd(total.previousValue)} kỳ trước</span>
        ) : null}
      </dd>
      <dd className="mt-4">
        <div
          className="flex h-2 overflow-hidden rounded-full bg-muted"
          role="img"
          aria-label={`PayOS ${formatDecimal1(payosShare)}%, tiền mặt ${formatDecimal1(100 - payosShare)}%`}
        >
          <span className="bg-chart-1" style={{ width: `${payosShare}%` }} />
          <span className="flex-1" style={CASH_HATCH_STYLE} />
        </div>
        <ul className="mt-2 grid grid-cols-2 gap-2 text-xs">
          <li>
            <span className="flex items-center gap-1.5 text-muted-foreground">
              <span className="size-2.5 rounded-sm bg-chart-1" aria-hidden />
              PayOS · {formatDecimal1(payosShare)}%
            </span>
            <span className="font-medium tabular-nums">
              {formatCurrencyVnd(payos.value)}
            </span>
          </li>
          <li>
            <span className="flex items-center gap-1.5 text-muted-foreground">
              <span
                className="size-2.5 rounded-sm"
                style={CASH_HATCH_STYLE}
                aria-hidden
              />
              Tiền mặt · {formatDecimal1(100 - payosShare)}%
            </span>
            <span className="font-medium tabular-nums">
              {formatCurrencyVnd(cash.value)}
            </span>
          </li>
        </ul>
      </dd>
    </div>
  );
}

function CountCell({
  label,
  value,
  previous,
}: {
  label: string;
  value: number;
  previous: number | null;
}) {
  return (
    <div className="bg-card p-5">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="mt-1 text-3xl font-semibold tracking-tight tabular-nums">
        {formatInteger(value)}
      </dd>
      <dd className="mt-1 flex flex-wrap items-center gap-x-2 text-xs text-muted-foreground">
        <DeltaChip value={value} previous={previous} />
        {previous !== null ? (
          <span>kỳ trước {formatInteger(previous)}</span>
        ) : null}
      </dd>
    </div>
  );
}

/** Một khung có vạch chia: ô doanh thu rộng gấp đôi, hai ô đếm còn lại cùng cỡ. */
export function OwnerKpiStrip({ data }: { data: OwnerAnalytics }) {
  return (
    <dl className="grid grid-cols-2 gap-px overflow-hidden rounded-xl border bg-border lg:grid-cols-[2fr_1fr_1fr]">
      <RevenueCell revenue={data.revenue} />
      <CountCell
        label="Lịch hẹn đã đặt"
        value={data.appointments.value}
        previous={data.appointments.previousValue}
      />
      <CountCell
        label="Khách đến khám"
        value={data.arrivals.value}
        previous={data.arrivals.previousValue}
      />
    </dl>
  );
}
