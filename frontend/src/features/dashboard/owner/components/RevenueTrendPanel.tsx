"use client";

import { useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDate } from "@/lib/format/formatDate";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { useElementWidth } from "../hooks/useElementWidth";
import type { DailyRevenuePoint } from "../types/ownerAnalytics";
import { formatCompactVnd } from "../lib/ownerAnalyticsFormat";
import { CASH_HATCH_STYLE } from "./OwnerKpiStrip";
import { OwnerPanel } from "./OwnerPanel";
import { RevenueChartSvg } from "./RevenueChartSvg";

const WEEKDAYS = [
  "Chủ nhật",
  "Thứ hai",
  "Thứ ba",
  "Thứ tư",
  "Thứ năm",
  "Thứ sáu",
  "Thứ bảy",
];
const weekdayOf = (date: string) =>
  WEEKDAYS[new Date(`${date}T00:00:00Z`).getUTCDay()];
const totalOf = (d: DailyRevenuePoint) => d.cash + d.payos;

function Legend() {
  return (
    <ul className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
      <li className="flex items-center gap-1.5">
        <span className="size-2.5 rounded-sm bg-chart-1" aria-hidden />
        PayOS
      </li>
      <li className="flex items-center gap-1.5">
        <span
          className="size-2.5 rounded-sm"
          style={CASH_HATCH_STYLE}
          aria-hidden
        />
        Tiền mặt
      </li>
      <li className="flex items-center gap-1.5">
        <svg width="18" height="6" aria-hidden>
          <line
            x1="0"
            y1="3"
            x2="18"
            y2="3"
            stroke="var(--chart-5)"
            strokeWidth="1.75"
            strokeDasharray="5 4"
          />
        </svg>
        Kỳ trước (theo thứ tự ngày)
      </li>
    </ul>
  );
}

/** Dòng đọc: chưa chọn ngày thì tóm tắt cả kỳ, đã chọn thì kê chi tiết ngày đó. */
function Readout({
  daily,
  activeIndex,
}: {
  daily: DailyRevenuePoint[];
  activeIndex: number | null;
}) {
  const summary = useMemo(() => {
    const total = daily.reduce((sum, d) => sum + totalOf(d), 0);
    const best = daily.reduce(
      (top, d) => (totalOf(d) > totalOf(top) ? d : top),
      daily[0],
    );
    return { total, best };
  }, [daily]);
  const day = activeIndex === null ? null : daily[activeIndex];

  return (
    <p className="min-h-10 text-sm tabular-nums" aria-live="polite">
      {day ? (
        <>
          <strong className="font-semibold">
            {weekdayOf(day.date)}, {formatDate(day.date)}
          </strong>
          <span className="text-muted-foreground"> · tổng </span>
          <strong className="font-semibold">
            {formatCurrencyVnd(totalOf(day))}
          </strong>
          <span className="text-muted-foreground">
            {" "}
            · PayOS {formatCurrencyVnd(day.payos)} · tiền mặt{" "}
            {formatCurrencyVnd(day.cash)}
          </span>
          {day.previousTotal !== null ? (
            <span className="text-muted-foreground">
              {" "}
              · ngày tương ứng kỳ trước {formatCurrencyVnd(day.previousTotal)}
            </span>
          ) : null}
        </>
      ) : (
        <span className="text-muted-foreground">
          Cả kỳ{" "}
          <strong className="font-semibold text-foreground">
            {formatCurrencyVnd(summary.total)}
          </strong>
          {summary.best && totalOf(summary.best) > 0 ? (
            <>
              {" "}
              · ngày cao nhất {formatDate(summary.best.date)} (
              {formatCompactVnd(totalOf(summary.best))}). Rê chuột hoặc dùng
              phím mũi tên để xem từng ngày.
            </>
          ) : null}
        </span>
      )}
    </p>
  );
}

function RevenueTable({ daily }: { daily: DailyRevenuePoint[] }) {
  return (
    <div className="max-h-[22rem] overflow-y-auto rounded-md border">
      <Table>
        <TableHeader className="sticky top-0 bg-card">
          <TableRow>
            <TableHead>Ngày</TableHead>
            <TableHead className="text-right">PayOS</TableHead>
            <TableHead className="text-right">Tiền mặt</TableHead>
            <TableHead className="text-right">Tổng</TableHead>
            <TableHead className="text-right">Kỳ trước</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {daily.map((d) => (
            <TableRow key={d.date}>
              <TableCell>
                {weekdayOf(d.date)}, {formatDate(d.date)}
              </TableCell>
              <TableCell className="text-right tabular-nums">
                {formatCurrencyVnd(d.payos)}
              </TableCell>
              <TableCell className="text-right tabular-nums">
                {formatCurrencyVnd(d.cash)}
              </TableCell>
              <TableCell className="text-right font-medium tabular-nums">
                {formatCurrencyVnd(totalOf(d))}
              </TableCell>
              <TableCell className="text-right tabular-nums text-muted-foreground">
                {d.previousTotal === null
                  ? "—"
                  : formatCurrencyVnd(d.previousTotal)}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

/** Khối doanh thu theo ngày: biểu đồ cột chồng, dòng đọc theo ngày chọn, và bảng số thay thế biểu đồ. */
export function RevenueTrendPanel({
  daily,
  className,
}: {
  daily: DailyRevenuePoint[];
  className?: string;
}) {
  const [asTable, setAsTable] = useState(false);
  const [activeIndex, setActiveIndex] = useState<number | null>(null);
  const { ref, width } = useElementWidth<HTMLDivElement>();
  const empty = daily.every((d) => totalOf(d) === 0);

  return (
    <OwnerPanel
      className={className}
      title="Doanh thu theo ngày"
      description="Tiền đã thu thật mỗi ngày, tách theo hình thức thanh toán"
      actions={
        <Button
          variant="outline"
          size="sm"
          aria-pressed={asTable}
          onClick={() => setAsTable((v) => !v)}
        >
          {asTable ? "Xem biểu đồ" : "Xem dạng bảng"}
        </Button>
      }
    >
      <div className="mb-3 space-y-2">
        <Legend />
        {asTable ? null : <Readout daily={daily} activeIndex={activeIndex} />}
      </div>
      {asTable ? (
        <RevenueTable daily={daily} />
      ) : empty ? (
        <p className="grid h-[264px] place-items-center text-sm text-muted-foreground">
          Chưa có khoản thanh toán nào trong kỳ này.
        </p>
      ) : (
        <div ref={ref} style={{ minHeight: 264 }}>
          {width > 0 ? (
            <RevenueChartSvg
              daily={daily}
              width={width}
              activeIndex={activeIndex}
              onActiveChange={setActiveIndex}
              ariaLabel="Biểu đồ doanh thu theo ngày. Dùng phím mũi tên trái phải để chọn ngày, Escape để bỏ chọn."
            />
          ) : null}
        </div>
      )}
    </OwnerPanel>
  );
}
