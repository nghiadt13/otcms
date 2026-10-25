import { useId } from "react";
import { DateRangeFilter } from "@/components/admin/DateRangeFilter";
import { FilterBar } from "@/components/admin/FilterBar";
import { Button } from "@/components/ui/button";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import type { ReviewFilters } from "../types/reviewModeration";
import {
  ALL_VALUE,
  fromUiValue,
  hasExtraFilters,
  toUiValue,
} from "../utils/reviewModerationLabels";

const RATING_OPTIONS = [
  { value: ALL_VALUE, label: "Tất cả" },
  ...[1, 2, 3, 4, 5].map((n) => ({ value: String(n), label: `${n} sao` })),
];
const REPLIED_OPTIONS = [
  { value: ALL_VALUE, label: "Tất cả" },
  { value: "true", label: "Đã phản hồi" },
  { value: "false", label: "Chưa phản hồi" },
];

// Nút đang chọn đổi màu nền ngữ nghĩa; không dựa vào mình màu để phân biệt (có aria-pressed).
const ITEM_CLASS =
  "aria-pressed:bg-primary aria-pressed:text-primary-foreground aria-pressed:hover:bg-primary";

type SingleChoiceProps = {
  label: string;
  value: string;
  options: { value: string; label: string }[];
  onChange: (value: string) => void;
};

/** ToggleGroup chọn một giá trị; bỏ chọn (value rỗng) quay về "Tất cả". */
function SingleChoice({ label, value, options, onChange }: SingleChoiceProps) {
  const labelId = useId();
  return (
    <div className="grid gap-1.5">
      <span id={labelId} className="text-sm leading-none font-medium">
        {label}
      </span>
      <ToggleGroup
        aria-labelledby={labelId}
        variant="outline"
        size="sm"
        spacing={1}
        className="flex-wrap"
        value={[toUiValue(value)]}
        onValueChange={(next) => onChange(fromUiValue(next[0]))}
      >
        {options.map((option) => (
          <ToggleGroupItem key={option.value} value={option.value} className={ITEM_CLASS}>
            {option.label}
          </ToggleGroupItem>
        ))}
      </ToggleGroup>
    </div>
  );
}

type ReviewFilterBarProps = {
  filters: ReviewFilters;
  dateDraft: { from: string; to: string };
  dateError?: string;
  onDateChange: (range: { from: string; to: string }) => void;
  onFilterChange: (next: Partial<ReviewFilters>) => void;
  onClear: () => void;
};

export function ReviewFilterBar({
  filters,
  dateDraft,
  dateError,
  onDateChange,
  onFilterChange,
  onClear,
}: ReviewFilterBarProps) {
  return (
    <FilterBar>
      <DateRangeFilter
        from={dateDraft.from}
        to={dateDraft.to}
        error={dateError}
        onChange={onDateChange}
      />
      <SingleChoice
        label="Số sao"
        value={filters.rating}
        options={RATING_OPTIONS}
        onChange={(rating) => onFilterChange({ rating: rating as ReviewFilters["rating"] })}
      />
      <SingleChoice
        label="Phản hồi"
        value={filters.replied}
        options={REPLIED_OPTIONS}
        onChange={(replied) => onFilterChange({ replied: replied as ReviewFilters["replied"] })}
      />
      {hasExtraFilters(filters) || dateDraft.from || dateDraft.to ? (
        <Button variant="link" onClick={onClear}>
          Xóa bộ lọc
        </Button>
      ) : null}
    </FilterBar>
  );
}
