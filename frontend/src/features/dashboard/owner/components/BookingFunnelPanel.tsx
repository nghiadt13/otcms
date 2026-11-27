import { formatInteger, formatDecimal1 } from "@/lib/format/formatNumber";
import type { BookingFunnel } from "../types/ownerAnalytics";
import { sharePercent } from "../lib/ownerAnalyticsFormat";
import { OwnerPanel } from "./OwnerPanel";

type Stage = { label: string; count: number; note: string };

/**
 * Đường đi của khách: đặt lịch, đến khám, hoàn thành. Mỗi bước kê số và tỷ lệ
 * bằng chữ, thanh chỉ để so độ dài; phần rơi ra giữa các bước nói rõ lý do.
 */
export function BookingFunnelPanel({
  funnel,
  className,
}: {
  funnel: BookingFunnel;
  className?: string;
}) {
  const { booked, arrived, completed, cancelled, noShow } = funnel;
  const stages: Stage[] = [
    { label: "Đặt lịch", count: booked, note: "Lịch hẹn trong kỳ" },
    {
      label: "Đến khám",
      count: arrived,
      note: `${formatDecimal1(sharePercent(arrived, booked))}% số đặt lịch`,
    },
    {
      label: "Hoàn thành",
      count: completed,
      note: `${formatDecimal1(sharePercent(completed, arrived))}% số đến khám`,
    },
  ];

  return (
    <OwnerPanel
      className={className}
      title="Từ đặt lịch đến hoàn thành"
      description="Khách rơi ra ở bước nào"
    >
      {booked === 0 ? (
        <p className="py-10 text-center text-sm text-muted-foreground">
          Chưa có lịch hẹn nào trong kỳ này.
        </p>
      ) : (
        <ol className="space-y-5">
          {stages.map((stage) => (
            <li key={stage.label}>
              <div className="flex items-baseline justify-between gap-3">
                <span className="text-sm font-medium">{stage.label}</span>
                <span className="text-lg font-semibold tabular-nums">
                  {formatInteger(stage.count)}
                </span>
              </div>
              <div
                className="mt-1.5 h-2.5 overflow-hidden rounded-full bg-muted"
                aria-hidden
              >
                <div
                  className="h-full rounded-full bg-chart-1"
                  style={{ width: `${sharePercent(stage.count, booked)}%` }}
                />
              </div>
              <p className="mt-1 text-xs text-muted-foreground">{stage.note}</p>
            </li>
          ))}
        </ol>
      )}
      <p className="mt-5 border-t pt-4 text-sm text-muted-foreground">
        Không đến khám:{" "}
        <span className="font-medium text-foreground tabular-nums">
          {formatInteger(cancelled)}
        </span>{" "}
        đã huỷ,{" "}
        <span className="font-medium text-foreground tabular-nums">
          {formatInteger(noShow)}
        </span>{" "}
        vắng mặt.
      </p>
    </OwnerPanel>
  );
}
