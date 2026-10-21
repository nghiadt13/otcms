"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import type { ShiftPeriod, ShiftView } from "../types/shift";
import {
  rangeContains,
  rangeLabel,
  type ShiftRange,
} from "../utils/shiftWeekRange";

type ShiftToolbarProps = {
  today: string;
  anchor: string;
  period: ShiftPeriod;
  view: ShiftView;
  range: ShiftRange;
  /** Lưới chỉ có từ 640px và ở kỳ Tuần. */
  canUseGrid: boolean;
  onPrev: () => void;
  onNext: () => void;
  onToday: () => void;
  onPeriodChange: (period: ShiftPeriod) => void;
  onViewChange: (view: ShiftView) => void;
};

/** Điều hướng kỳ (‹ Hôm nay ›), độ rộng kỳ Tuần|Ngày và chế độ Lưới|Danh sách. */
export function ShiftToolbar({
  today,
  anchor,
  period,
  view,
  range,
  canUseGrid,
  onPrev,
  onNext,
  onToday,
  onPeriodChange,
  onViewChange,
}: ShiftToolbarProps) {
  const unit = period === "week" ? "tuần" : "ngày";
  return (
    <div className="mb-3 flex flex-wrap items-center gap-x-4 gap-y-2">
      <div className="flex items-center gap-1.5">
        <Button variant="outline" size="icon" aria-label="Kỳ trước" title={`Lùi một ${unit}`} onClick={onPrev}>
          <ChevronLeft />
        </Button>
        <Button variant="outline" disabled={rangeContains(range, today)} onClick={onToday}>
          Hôm nay
        </Button>
        <Button variant="outline" size="icon" aria-label="Kỳ sau" title={`Tiến một ${unit}`} onClick={onNext}>
          <ChevronRight />
        </Button>
      </div>
      <p className="text-sm font-medium tabular-nums" aria-live="polite">
        {rangeLabel(anchor, period)}
      </p>
      <div className="flex items-center gap-2 sm:ml-auto">
        <ToggleGroup
          variant="outline"
          spacing={0}
          aria-label="Độ rộng kỳ"
          value={[period]}
          onValueChange={(next) => {
            const value = next[0] as ShiftPeriod | undefined;
            if (value) onPeriodChange(value);
          }}
        >
          <ToggleGroupItem value="week" aria-label="Xem theo tuần">Tuần</ToggleGroupItem>
          <ToggleGroupItem value="day" aria-label="Xem theo ngày">Ngày</ToggleGroupItem>
        </ToggleGroup>
        {canUseGrid ? (
          <ToggleGroup
            variant="outline"
            spacing={0}
            aria-label="Chế độ xem"
            value={[view]}
            onValueChange={(next) => {
              const value = next[0] as ShiftView | undefined;
              if (value) onViewChange(value);
            }}
          >
            <ToggleGroupItem value="grid" aria-label="Xem dạng lưới">Lưới</ToggleGroupItem>
            <ToggleGroupItem value="list" aria-label="Xem dạng danh sách">Danh sách</ToggleGroupItem>
          </ToggleGroup>
        ) : null}
      </div>
    </div>
  );
}
