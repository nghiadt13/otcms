"use client";

import { DateRangeFilter } from "@/components/admin/DateRangeFilter";
import { FilterBar } from "@/components/admin/FilterBar";
import { SearchField } from "@/components/admin/SearchField";
import { Label } from "@/components/ui/label";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import { formatAppMessage } from "@/lib/messages/appMessages";
import type { MsgCode } from "@/lib/api/apiError";
import { CATEGORY_FILTER_LABELS } from "../labels/activityLabels";
import type {
  ActivityCategory,
  ActivityLogFilters,
} from "../types/activityLog";

type ActivityLogFilterBarProps = {
  draft: ActivityLogFilters;
  onChange: (patch: Partial<ActivityLogFilters>) => void;
  rangeError: MsgCode | null;
};

export function ActivityLogFilterBar({
  draft,
  onChange,
  rangeError,
}: ActivityLogFilterBarProps) {
  return (
    <FilterBar>
      <DateRangeFilter
        from={draft.from}
        to={draft.to}
        onChange={onChange}
        error={rangeError ? formatAppMessage(rangeError) : undefined}
      />
      <div className="grid w-full gap-1.5 sm:w-52">
        <Label htmlFor="activity-log-category">Loại hành động</Label>
        <NativeSelect
          id="activity-log-category"
          className="w-full"
          value={draft.action ?? ""}
          onChange={(event) =>
            onChange({
              action: (event.target.value || null) as ActivityCategory | null,
            })
          }
        >
          <NativeSelectOption value="">Tất cả</NativeSelectOption>
          {(
            Object.entries(CATEGORY_FILTER_LABELS) as [
              ActivityCategory,
              string,
            ][]
          ).map(([value, label]) => (
            <NativeSelectOption key={value} value={value}>
              {label}
            </NativeSelectOption>
          ))}
        </NativeSelect>
      </div>
      <SearchField
        label="Bản ghi bị tác động"
        placeholder="Bản ghi: nhập mã hoặc tên"
        value={draft.record}
        onChange={(record) => onChange({ record })}
      />
      <SearchField
        label="Người thực hiện"
        placeholder="Người thực hiện: tên đăng nhập"
        value={draft.actor}
        onChange={(actor) => onChange({ actor })}
      />
    </FilterBar>
  );
}
