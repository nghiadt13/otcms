import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatDecimal1 } from "@/lib/format/formatNumber";
import { sharePercent } from "../lib/ownerAnalyticsFormat";

/** Ô doanh thu: số tiền, rồi thanh tỷ trọng so với tổng của cả bảng kèm phần trăm bằng chữ. */
export function RevenueShareCell({
  revenue,
  total,
}: {
  revenue: number;
  total: number;
}) {
  const share = sharePercent(revenue, total);
  return (
    <div className="ml-auto w-28 sm:w-44">
      <div className="font-medium tabular-nums">
        {formatCurrencyVnd(revenue)}
      </div>
      <div className="mt-1 flex items-center gap-2">
        <div
          className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted"
          aria-hidden
        >
          <div
            className="h-full rounded-full bg-chart-1"
            style={{ width: `${share}%`, minWidth: revenue > 0 ? 3 : 0 }}
          />
        </div>
        <span className="w-12 text-right text-xs text-muted-foreground tabular-nums">
          {formatDecimal1(share)}%
        </span>
      </div>
    </div>
  );
}
