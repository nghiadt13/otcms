"use client";

import { CalendarDays, Users, type LucideIcon } from "lucide-react";
import { useId, type ReactNode } from "react";
import { Label } from "@/components/ui/label";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import type { LinkedProfile } from "@/lib/patient-scope/types";
import { cn } from "@/lib/utils";
import {
  RESULT_TIME_RANGES,
  type ResultTimeRange,
} from "./resultVisitPresentation";

// Hai ô lọc của danh sách: người khám (chỉ hiện khi có từ hai hồ sơ) và khoảng thời gian.
// Nhãn chỉ cho trình đọc màn hình; ô chọn native để dùng ổn trên điện thoại.
function IconSelect({
  icon: Icon,
  label,
  className,
  children,
  value,
  onChange,
}: {
  icon: LucideIcon;
  label: string;
  className?: string;
  children: ReactNode;
  value: string;
  onChange: (value: string) => void;
}) {
  const id = useId();
  return (
    <div className={cn("relative", className)}>
      <Label className="sr-only" htmlFor={id}>
        {label}
      </Label>
      <Icon
        aria-hidden
        className="pointer-events-none absolute top-1/2 left-3.5 z-10 size-5 -translate-y-1/2 text-muted-foreground"
      />
      <NativeSelect
        className="w-full [&_select]:h-12 [&_select]:pr-10 [&_select]:pl-11 [&_select]:text-base"
        id={id}
        onChange={(event) => onChange(event.target.value)}
        value={value}
      >
        {children}
      </NativeSelect>
    </div>
  );
}

export function ResultFilters({
  profiles,
  profileId,
  onProfileChange,
  timeRange,
  onTimeRangeChange,
}: {
  profiles: LinkedProfile[];
  profileId: string | undefined;
  onProfileChange: (profileId: string | undefined) => void;
  timeRange: ResultTimeRange;
  onTimeRangeChange: (range: ResultTimeRange) => void;
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_minmax(0,14rem)]">
      {profiles.length > 1 ? (
        <IconSelect
          icon={Users}
          label="Người khám"
          onChange={(value) => onProfileChange(value || undefined)}
          value={profileId ?? ""}
        >
          <NativeSelectOption value="">Tất cả người khám</NativeSelectOption>
          {profiles.map((profile) => (
            <NativeSelectOption key={profile.id} value={profile.id}>
              {`${profile.fullName} · ${CARE_RELATIONSHIP_LABELS[profile.relationship]}`}
            </NativeSelectOption>
          ))}
        </IconSelect>
      ) : null}
      <IconSelect
        className={profiles.length > 1 ? undefined : "sm:col-span-2"}
        icon={CalendarDays}
        label="Khoảng thời gian"
        onChange={(value) => onTimeRangeChange(value as ResultTimeRange)}
        value={timeRange}
      >
        {RESULT_TIME_RANGES.map((range) => (
          <NativeSelectOption key={range.value} value={range.value}>
            {range.label}
          </NativeSelectOption>
        ))}
      </IconSelect>
    </div>
  );
}
