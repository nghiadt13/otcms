"use client";

import { Button } from "@/components/ui/button";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { DateRangeFilter } from "@/components/admin/DateRangeFilter";
import { FilterBar } from "@/components/admin/FilterBar";
import { QUICK_PRESETS, type QuickPreset } from "../lib/dashboardPeriodRules";
import type { DashboardPeriod } from "../types/dashboardOverview";

type DashboardPeriodToolbarProps = {
  preset: QuickPreset | null;
  draft: DashboardPeriod;
  /** Câu lỗi kỳ không hợp lệ, hiện dưới ô ngày. */
  error?: string;
  loading: boolean;
  /** true khi không đủ quyền: khóa cả thanh. */
  disabled: boolean;
  onPresetSelect: (preset: QuickPreset) => void;
  onDraftChange: (range: DashboardPeriod) => void;
  onApply: () => void;
};

/** Chọn kỳ: kỳ nhanh tải ngay; khoảng ngày tùy chọn chỉ tải khi bấm Xem. */
export function DashboardPeriodToolbar({
  preset,
  draft,
  error,
  loading,
  disabled,
  onPresetSelect,
  onDraftChange,
  onApply,
}: DashboardPeriodToolbarProps) {
  return (
    <FilterBar>
      <ToggleGroup
        aria-label="Chọn kỳ nhanh"
        variant="outline"
        spacing={1}
        className="flex-wrap"
        value={preset ? [preset] : []}
        disabled={disabled}
        onValueChange={(next) => {
          // Bấm lại nút đang chọn không làm rỗng nhóm.
          const chosen = next[0] as QuickPreset | undefined;
          if (chosen) onPresetSelect(chosen);
        }}
      >
        {QUICK_PRESETS.map((item) => (
          <ToggleGroupItem key={item.value} value={item.value}>
            {item.label}
          </ToggleGroupItem>
        ))}
      </ToggleGroup>
      <DateRangeFilter
        from={draft.from}
        to={draft.to}
        onChange={onDraftChange}
        error={error}
      />
      <Button
        className="w-full sm:w-auto"
        disabled={disabled || loading || !draft.from || !draft.to}
        onClick={onApply}
      >
        {loading ? "Đang tải..." : "Xem"}
      </Button>
    </FilterBar>
  );
}
