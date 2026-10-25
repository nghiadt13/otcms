import { cn } from "@/lib/utils";
import {
  describeComparison,
  formatMetricValue,
} from "../lib/describeComparison";
import type { DashboardSummaryCell } from "../lib/dashboardSummaryCells";

/**
 * Dải 5 chỉ số chính trong một khung viền, ngăn bằng vạch chia (không phải 5 thẻ rời).
 * `gap-px` trên nền `bg-border` tạo vạch chia ở cả hai chiều khi xuống 2 cột.
 */
export function DashboardSummaryStrip({
  cells,
}: {
  cells: DashboardSummaryCell[];
}) {
  return (
    <dl className="mb-8 grid grid-cols-2 gap-px overflow-hidden rounded-lg border bg-border lg:grid-cols-5">
      {cells.map((cell, index) => (
        <div
          key={cell.key}
          className={cn(
            "bg-card p-4",
            // Số lẻ ô: ô cuối chiếm trọn hàng ở 2 cột.
            index === cells.length - 1 && "col-span-2 lg:col-span-1",
          )}
        >
          <dt className="text-sm text-muted-foreground">{cell.label}</dt>
          <dd className="mt-1 text-xl font-semibold tracking-tight tabular-nums sm:text-2xl">
            {formatMetricValue(cell.value, cell.format)}
          </dd>
          <dd className="mt-1 text-xs text-muted-foreground">
            {cell.noSource
              ? "Chưa có nguồn dữ liệu"
              : (cell.note ?? describeComparison(cell))}
          </dd>
        </div>
      ))}
    </dl>
  );
}
