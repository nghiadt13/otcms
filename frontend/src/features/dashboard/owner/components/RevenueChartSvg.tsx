"use client";

import { useId, type KeyboardEvent, type PointerEvent } from "react";
import type { DailyRevenuePoint } from "../types/ownerAnalytics";
import { formatCompactVnd, niceAxisMax } from "../lib/ownerAnalyticsFormat";

const HEIGHT = 264;
const MARGIN = { top: 12, right: 8, bottom: 30, left: 62 };
const TICKS = 4;
/** Nhãn ngày cần ít nhất chừng này px để không đè nhau. */
const LABEL_SLOT_PX = 52;

const dayMonth = (date: string) => `${date.slice(8, 10)}/${date.slice(5, 7)}`;

type Props = {
  daily: DailyRevenuePoint[];
  width: number;
  activeIndex: number | null;
  onActiveChange: (index: number | null) => void;
  /** Mô tả cách dùng bàn phím, đọc cho người dùng trình đọc màn hình. */
  ariaLabel: string;
};

/**
 * Cột chồng theo ngày: PayOS đặc ở dưới, tiền mặt gạch chéo ở trên, đường nét
 * đứt là tổng ngày cùng thứ tự của kỳ trước. Vẽ bằng SVG thuần; chọn ngày
 * bằng chuột hoặc phím mũi tên, chi tiết hiện ở dòng đọc phía trên biểu đồ.
 */
export function RevenueChartSvg({
  daily,
  width,
  activeIndex,
  onActiveChange,
  ariaLabel,
}: Props) {
  const patternId = useId();
  const innerW = Math.max(0, width - MARGIN.left - MARGIN.right);
  const innerH = HEIGHT - MARGIN.top - MARGIN.bottom;
  const slot = daily.length ? innerW / daily.length : 0;
  const barW = Math.max(2, Math.min(30, slot * 0.64));

  const peak = Math.max(
    0,
    ...daily.map((d) => d.cash + d.payos),
    ...daily.map((d) => d.previousTotal ?? 0),
  );
  const axisMax = niceAxisMax(peak);
  const y = (value: number) => MARGIN.top + innerH - (value / axisMax) * innerH;
  const cx = (index: number) => MARGIN.left + slot * index + slot / 2;

  const stride = Math.max(1, Math.ceil(LABEL_SLOT_PX / Math.max(slot, 1)));
  const previousPath = daily
    .map((d, i) =>
      d.previousTotal === null
        ? null
        : `${i === 0 ? "M" : "L"}${cx(i).toFixed(1)} ${y(d.previousTotal).toFixed(1)}`,
    )
    .filter(Boolean)
    .join(" ");

  function indexAt(event: PointerEvent<SVGSVGElement>): number | null {
    const box = event.currentTarget.getBoundingClientRect();
    const x = event.clientX - box.left - MARGIN.left;
    if (x < 0 || x > innerW || slot === 0) return null;
    return Math.min(daily.length - 1, Math.floor(x / slot));
  }

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const last = daily.length - 1;
    const at = activeIndex ?? -1;
    const moves: Record<string, number | null> = {
      ArrowRight: Math.min(last, at + 1),
      ArrowLeft: at <= 0 ? 0 : at - 1,
      Home: 0,
      End: last,
      Escape: null,
    };
    if (!(event.key in moves)) return;
    event.preventDefault();
    onActiveChange(moves[event.key]);
  }

  return (
    <div
      role="group"
      aria-label={ariaLabel}
      tabIndex={0}
      onKeyDown={onKeyDown}
      onBlur={() => onActiveChange(null)}
      className="rounded-md outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
    >
      <svg
        width={width}
        height={HEIGHT}
        viewBox={`0 0 ${width} ${HEIGHT}`}
        role="img"
        aria-hidden
        className="block touch-pan-y select-none"
        onPointerMove={(event) => onActiveChange(indexAt(event))}
        onPointerLeave={() => onActiveChange(null)}
      >
        <defs>
          <pattern
            id={patternId}
            width="6"
            height="6"
            patternUnits="userSpaceOnUse"
            patternTransform="rotate(45)"
          >
            <rect width="6" height="6" fill="var(--chart-2)" />
            <line
              x1="0"
              y1="0"
              x2="0"
              y2="6"
              stroke="var(--color-coral-strong)"
              strokeWidth="2.2"
              opacity="0.55"
            />
          </pattern>
        </defs>

        {Array.from({ length: TICKS + 1 }, (_, i) => {
          const value = (axisMax / TICKS) * i;
          return (
            <g key={i}>
              <line
                x1={MARGIN.left}
                x2={width - MARGIN.right}
                y1={y(value)}
                y2={y(value)}
                stroke="var(--border)"
                strokeDasharray={i === 0 ? undefined : "2 4"}
              />
              <text
                x={MARGIN.left - 8}
                y={y(value)}
                textAnchor="end"
                dominantBaseline="middle"
                className="fill-muted-foreground text-[11px] tabular-nums"
              >
                {i === 0 ? "0" : formatCompactVnd(value).replace(" ₫", "")}
              </text>
            </g>
          );
        })}

        {activeIndex !== null ? (
          <rect
            x={MARGIN.left + slot * activeIndex}
            y={MARGIN.top}
            width={slot}
            height={innerH}
            className="fill-muted"
          />
        ) : null}

        {/* Đổi kỳ thì key đổi: cột mọc lại từ trục, báo hiệu số liệu mới đã về. */}
        <g key={`${daily[0]?.date}-${daily.length}`}>
          {daily.map((d, i) => {
            const payosH = (d.payos / axisMax) * innerH;
            const cashH = (d.cash / axisMax) * innerH;
            const x = cx(i) - barW / 2;
            return (
              <g
                key={d.date}
                className="owner-bar"
                opacity={activeIndex === null || activeIndex === i ? 1 : 0.55}
              >
                <rect
                  x={x}
                  y={y(0) - payosH}
                  width={barW}
                  height={payosH}
                  rx={1.5}
                  fill="var(--chart-1)"
                />
                <rect
                  x={x}
                  y={y(0) - payosH - cashH}
                  width={barW}
                  height={cashH}
                  rx={1.5}
                  fill={`url(#${patternId})`}
                />
              </g>
            );
          })}
        </g>

        {previousPath ? (
          <path
            d={previousPath}
            fill="none"
            stroke="var(--chart-5)"
            strokeWidth="1.75"
            strokeDasharray="5 4"
            strokeLinejoin="round"
          />
        ) : null}

        {daily.map((d, i) =>
          i % stride === 0 ? (
            <text
              key={d.date}
              x={cx(i)}
              y={HEIGHT - 10}
              textAnchor="middle"
              className="fill-muted-foreground text-[11px] tabular-nums"
            >
              {dayMonth(d.date)}
            </text>
          ) : null,
        )}
      </svg>
    </div>
  );
}
