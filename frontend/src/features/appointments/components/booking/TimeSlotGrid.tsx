"use client";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { formatTime } from "@/lib/format/formatDate";
import type { BookingVariant, OpenTime } from "../../types/booking";
import { bookingSize } from "./bookingSizes";

// Lưới giờ trống của một ngày, chia buổi sáng và buổi chiều. Nút chọn có `aria-pressed`.
// Nhóm có `data-autofocus` để khối đưa focus vào lưới khi mở lại (giờ vừa bị giữ).

export function TimeSlotGrid({
  morning,
  afternoon,
  loading,
  dayClosed,
  value,
  onChange,
  variant,
  label,
}: {
  morning: readonly OpenTime[];
  afternoon: readonly OpenTime[];
  loading: boolean;
  /** Ngày không còn giờ: hiện câu gợi ý chọn ngày khác. */
  dayClosed: boolean;
  /** ISO +07:00 của giờ đang chọn. */
  value: string | null;
  onChange: (startAt: string) => void;
  variant: BookingVariant;
  label: string;
}) {
  const sizes = bookingSize(variant);

  if (loading)
    return (
      <div className={`grid gap-2 ${sizes.grid}`} aria-hidden>
        {Array.from({ length: 8 }, (_, index) => (
          <Skeleton key={index} className={`${sizes.control} rounded-lg`} />
        ))}
      </div>
    );
  if (dayClosed)
    return (
      <p className={`${sizes.body} text-muted-foreground`}>
        Ngày này đã kín lịch. Chọn ngày khác có chữ Còn giờ.
      </p>
    );

  const renderPeriod = (title: string, times: readonly OpenTime[]) =>
    times.length === 0 ? null : (
      <div role="group" aria-label={title} className="grid gap-2">
        <p className={`${sizes.detail} font-medium`}>{title}</p>
        <div className={`grid gap-2 ${sizes.grid}`}>
          {times.map((time) => {
            const selected = time.startAt === value;
            return (
              <Button
                key={time.startAt}
                type="button"
                variant={selected ? "secondary" : "outline"}
                aria-pressed={selected}
                className={`${sizes.control} tabular-nums ${selected ? "border-primary font-semibold text-primary ring-1 ring-primary" : ""}`}
                onClick={() => onChange(time.startAt)}
              >
                {formatTime(time.startAt)}
              </Button>
            );
          })}
        </div>
      </div>
    );

  return (
    <div
      role="group"
      aria-label={label}
      tabIndex={-1}
      data-autofocus
      className="grid gap-5"
    >
      {renderPeriod("Buổi sáng", morning)}
      {renderPeriod("Buổi chiều", afternoon)}
    </div>
  );
}
