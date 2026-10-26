"use client";

import { Plus } from "lucide-react";
import { memo } from "react";
import { Button } from "@/components/ui/button";
import { ROLE_LABELS } from "@/lib/domain/clinicRoles";
import { cn } from "@/lib/utils";
import type { Shift } from "../types/shift";
import { dayMonthYear } from "../utils/shiftWeekRange";

type ShiftCellProps = {
  staffId: string;
  staffName: string;
  date: string;
  /** Ca của nhân viên trong ngày, đã sắp theo giờ. */
  shifts: Shift[];
  past: boolean;
  row: number;
  col: number;
  /** Ô đang giữ điểm Tab của lưới (roving tabindex). */
  active: boolean;
  onActivate: (row: number, col: number) => void;
  onOpenShift: (shift: Shift) => void;
  onAddShift: (staffId: string, date: string) => void;
};

const gridChip =
  "w-full min-w-0 flex-col items-start gap-0 rounded-md bg-secondary px-2 py-1.5 text-left whitespace-normal text-secondary-foreground hover:bg-muted";

/** Một ô lưới: 0..n chip ca xếp chồng, ô trống là một nút thêm ca. */
function ShiftCellView({
  staffId,
  staffName,
  date,
  shifts,
  past,
  row,
  col,
  active,
  onActivate,
  onOpenShift,
  onAddShift,
}: ShiftCellProps) {
  const addLabel = `Thêm ca cho ${staffName}, ${dayMonthYear(date)}`;
  const primaryProps = (isPrimary: boolean) =>
    isPrimary
      ? {
          "data-grid-primary": `${row},${col}`,
          tabIndex: active ? 0 : -1,
          onFocus: () => onActivate(row, col),
        }
      : { tabIndex: active ? 0 : -1 };

  if (shifts.length === 0) {
    return (
      <Button
        variant="ghost"
        {...primaryProps(true)}
        aria-label={addLabel}
        aria-disabled={past || undefined}
        title={past ? "Ngày đã qua, chỉ xem" : addLabel}
        onClick={() => {
          if (!past) onAddShift(staffId, date);
        }}
        className={cn(
          "group/empty h-full min-h-14 w-full rounded-md text-muted-foreground",
          past && "cursor-not-allowed opacity-40 hover:bg-transparent",
        )}
      >
        {past ? null : (
          <Plus className="opacity-0 transition-opacity duration-150 group-hover/empty:opacity-100 group-focus-visible/empty:opacity-100 motion-reduce:transition-none" />
        )}
      </Button>
    );
  }

  return (
    <div className="flex flex-col gap-1">
      {shifts.map((shift, index) => (
        <Button
          key={shift.id}
          variant="ghost"
          {...primaryProps(index === 0)}
          aria-label={`Ca ${shift.startTime}–${shift.endTime} tại ${shift.roomName} của ${shift.staffName}. ${
            shift.allowedActions.includes("edit") ? "Sửa ca" : "Xem ca"
          }`}
          onClick={() => onOpenShift(shift)}
          className={cn(
            gridChip,
            shift.allowedActions.includes("edit")
              ? ""
              : "bg-muted text-muted-foreground",
          )}
        >
          <span className="text-xs font-medium tabular-nums">
            {shift.startTime}–{shift.endTime}
          </span>
          <span className="w-full truncate text-xs">{shift.roomName}</span>
          <span className="w-full truncate text-xs text-muted-foreground">
            {ROLE_LABELS[shift.role]}
          </span>
        </Button>
      ))}
      {past ? null : (
        <Button
          variant="ghost"
          size="icon-xs"
          tabIndex={active ? 0 : -1}
          aria-label={addLabel}
          title={addLabel}
          className="self-start text-muted-foreground"
          onClick={() => onAddShift(staffId, date)}
        >
          <Plus />
        </Button>
      )}
    </div>
  );
}

export const ShiftCell = memo(ShiftCellView);
