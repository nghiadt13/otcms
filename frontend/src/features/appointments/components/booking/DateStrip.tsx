"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { useRef } from "react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { AppMessageText } from "./AppMessageText";
import { BookingLoadError } from "./BookingLoadError";
import { bookingSize } from "./bookingSizes";
import {
  longWeekdayLabel,
  shortDateLabel,
  shortWeekdayLabel,
} from "../../schema/bookingTime";
import type { BookingVariant, OpenDay } from "../../types/booking";

// Dải 14 ngày cuộn ngang. Ô "Hết giờ" vẫn bấm được: hiện trống và gợi ý chọn ngày khác.

const SCROLL_STEP_PX = 240;

export function DateStrip({
  days,
  loading,
  failed,
  selected,
  onSelect,
  onRetry,
  allClosed,
  variant,
  label,
}: {
  /** Đã lọc giờ đã qua. `null` khi chưa có dữ liệu. */
  days: readonly OpenDay[] | null;
  loading: boolean;
  failed: boolean;
  selected: string | null;
  onSelect: (date: string) => void;
  onRetry: () => void;
  /** Cả dải không còn giờ: hiện câu MSG-25 trong khối. */
  allClosed: boolean;
  variant: BookingVariant;
  label: string;
}) {
  const sizes = bookingSize(variant);
  const scrollRef = useRef<HTMLDivElement>(null);

  if (loading)
    return (
      <div className="flex gap-2 overflow-hidden" aria-hidden>
        {Array.from({ length: 7 }, (_, index) => (
          <Skeleton
            key={index}
            className={`${sizes.cell} shrink-0 rounded-lg`}
          />
        ))}
      </div>
    );
  if (failed)
    return (
      <BookingLoadError
        message="Không tải được giờ trống."
        onRetry={onRetry}
        variant={variant}
      />
    );
  if (!days) return null;

  const scroll = (direction: -1 | 1) =>
    scrollRef.current?.scrollBy({
      left: direction * SCROLL_STEP_PX,
      behavior: "auto",
    });

  return (
    <div className="grid gap-3">
      <div className="flex items-center gap-2">
        <div
          ref={scrollRef}
          className="min-w-0 flex-1 snap-x snap-mandatory overflow-x-auto pb-1"
        >
          <ToggleGroup
            aria-label={label}
            value={selected ? [selected] : []}
            onValueChange={(values: string[]) => {
              const next = values.find((value) => value !== selected);
              if (next !== undefined) onSelect(next);
            }}
            className="w-max flex-nowrap gap-2"
          >
            {days.map((day) => {
              const open = day.times.length > 0;
              return (
                <ToggleGroupItem
                  key={day.date}
                  value={day.date}
                  aria-label={`${longWeekdayLabel(day.date)} ${shortDateLabel(day.date)}, ${open ? "còn giờ" : "hết giờ"}`}
                  className={`${sizes.cell} h-auto snap-start flex-col gap-0.5 rounded-lg border px-1 py-2 ${sizes.body}`}
                >
                  <span className={sizes.detail}>
                    {shortWeekdayLabel(day.date)}
                  </span>
                  <span className="font-medium tabular-nums">
                    {shortDateLabel(day.date)}
                  </span>
                  <span className={`${sizes.detail} whitespace-nowrap`}>
                    {open ? "Còn giờ" : "Hết giờ"}
                  </span>
                </ToggleGroupItem>
              );
            })}
          </ToggleGroup>
        </div>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label="Ngày trước"
          className="hidden md:inline-flex"
          onClick={() => scroll(-1)}
        >
          <ChevronLeft />
        </Button>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label="Ngày sau"
          className="hidden md:inline-flex"
          onClick={() => scroll(1)}
        >
          <ChevronRight />
        </Button>
      </div>
      {allClosed ? (
        <AppMessageText code="MSG-25" variant={variant} tone="warning" />
      ) : null}
    </div>
  );
}
