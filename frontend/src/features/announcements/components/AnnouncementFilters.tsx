"use client";

import { useId } from "react";
import { DateRangeFilter } from "@/components/admin/DateRangeFilter";
import { FilterBar } from "@/components/admin/FilterBar";
import { SearchField } from "@/components/admin/SearchField";
import { Label } from "@/components/ui/label";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import type {
  AnnouncementAudience,
  AnnouncementFilters as Filters,
} from "../types/announcement";
import { audienceLabel } from "./announcementLabels";

type AnnouncementFiltersProps = {
  searchInput: string;
  onSearchChange: (value: string) => void;
  audience: Filters["audience"];
  /** Mã nhóm từ options; null khi options chưa có thì khóa ô chọn. */
  audiences: AnnouncementAudience[] | null;
  onAudienceChange: (value: Filters["audience"]) => void;
  range: { from: string; to: string };
  onRangeChange: (range: { from: string; to: string }) => void;
  dateError?: string;
};

export function AnnouncementFilters({
  searchInput,
  onSearchChange,
  audience,
  audiences,
  onAudienceChange,
  range,
  onRangeChange,
  dateError,
}: AnnouncementFiltersProps) {
  const id = useId();
  return (
    <FilterBar>
      <SearchField
        value={searchInput}
        onChange={onSearchChange}
        placeholder="Tìm theo tiêu đề"
        label="Tìm thông báo theo tiêu đề"
      />
      <div className="grid w-full gap-1.5 sm:w-56">
        <Label htmlFor={`${id}-audience`}>Nhóm người nhận</Label>
        <NativeSelect
          id={`${id}-audience`}
          className="w-full"
          value={audience}
          disabled={audiences === null}
          onChange={(event) =>
            onAudienceChange(event.target.value as Filters["audience"])
          }
        >
          <NativeSelectOption value="">Tất cả nhóm</NativeSelectOption>
          {(audiences ?? []).map((code) => (
            <NativeSelectOption key={code} value={code}>
              {audienceLabel(code)}
            </NativeSelectOption>
          ))}
        </NativeSelect>
      </div>
      <DateRangeFilter
        from={range.from}
        to={range.to}
        onChange={onRangeChange}
        error={dateError}
      />
    </FilterBar>
  );
}
