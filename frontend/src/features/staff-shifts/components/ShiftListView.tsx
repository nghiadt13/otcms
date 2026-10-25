"use client";

import { Eye, Pencil, Trash2 } from "lucide-react";
import { Fragment } from "react";
import { RowActionsMenu, type RowAction } from "@/components/admin/RowActionsMenu";
import { TableFrame } from "@/components/admin/TableFrame";
import { TableStateRows } from "@/components/admin/TableStateRows";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ROLE_LABELS } from "@/lib/domain/clinicRoles";
import { ROOM_CATEGORY_LABELS } from "@/lib/domain/clinicCategories";
import type { Shift, ShiftPeriod } from "../types/shift";
import { dayMonthYear, weekdayShort } from "../utils/shiftWeekRange";

type ShiftListViewProps = {
  shifts: Shift[];
  period: ShiftPeriod;
  loading: boolean;
  error: string | null;
  /** Không truyền thì ẩn "Thử lại" (lỗi 403). */
  onRetry?: () => void;
  filtered: boolean;
  onOpenShift: (shift: Shift) => void;
  onDeleteShift: (shift: Shift) => void;
};

/** Danh sách ca theo giờ; nhóm theo ngày khi kỳ là Tuần. Không phân trang (trả hết kỳ). */
export function ShiftListView({
  shifts,
  period,
  loading,
  error,
  onRetry,
  filtered,
  onOpenShift,
  onDeleteShift,
}: ShiftListViewProps) {
  const ready = !loading && !error;
  const emptyText = filtered
    ? "Không có ca khớp bộ lọc. Bỏ bớt lọc hoặc chọn kỳ khác."
    : period === "week"
      ? "Tuần này chưa có ca nào. Bấm Thêm ca để xếp ca cho nhân viên."
      : "Ngày này chưa có ca nào. Bấm Thêm ca để xếp ca cho nhân viên.";

  const actionsOf = (shift: Shift): RowAction[] => {
    const allowed = shift.allowedActions;
    const actions: RowAction[] = [];
    if (allowed.includes("edit"))
      actions.push({ key: "edit", label: "Sửa", icon: Pencil, onSelect: () => onOpenShift(shift) });
    if (allowed.includes("view"))
      actions.push({ key: "view", label: "Xem", icon: Eye, onSelect: () => onOpenShift(shift) });
    if (allowed.includes("delete"))
      actions.push({
        key: "delete",
        label: "Xóa ca",
        icon: Trash2,
        destructive: true,
        onSelect: () => onDeleteShift(shift),
      });
    return actions;
  };

  const renderRow = (shift: Shift) => (
    <TableRow key={shift.id}>
      <TableCell className="whitespace-nowrap">
        <Button
          variant="ghost"
          size="sm"
          className="font-medium tabular-nums"
          aria-label={`${shift.allowedActions.includes("edit") ? "Sửa" : "Xem"} ca ${shift.startTime}–${shift.endTime} của ${shift.staffName}`}
          onClick={() => onOpenShift(shift)}
        >
          {shift.startTime}–{shift.endTime}
        </Button>
      </TableCell>
      <TableCell>
        <span className="block font-medium">{shift.staffName}</span>
        <span className="block text-xs text-muted-foreground">{ROLE_LABELS[shift.role]}</span>
      </TableCell>
      <TableCell>
        <span className="block">{shift.roomName}</span>
        <span className="block text-xs text-muted-foreground">
          {ROOM_CATEGORY_LABELS[shift.roomCategory]}
        </span>
      </TableCell>
      <TableCell className="w-10 text-right">
        <RowActionsMenu actions={actionsOf(shift)} label={`ca của ${shift.staffName}`} />
      </TableCell>
    </TableRow>
  );

  // Gom theo ngày (danh sách đã sắp theo ngày, giờ).
  const groups: { date: string; items: Shift[] }[] = [];
  for (const shift of shifts) {
    const last = groups[groups.length - 1];
    if (last && last.date === shift.date) last.items.push(shift);
    else groups.push({ date: shift.date, items: [shift] });
  }

  return (
    <TableFrame>
      <Table className="min-w-[32rem]">
        <TableHeader>
          <TableRow>
            <TableHead>Giờ</TableHead>
            <TableHead>Nhân viên</TableHead>
            <TableHead>Phòng</TableHead>
            <TableHead className="w-10"><span className="sr-only">Thao tác</span></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableStateRows
            colSpan={4}
            loading={loading}
            error={error}
            onRetry={onRetry}
            empty={ready && shifts.length === 0}
            emptyText={emptyText}
          />
          {ready
            ? period === "week"
              ? groups.map((group) => (
                  <Fragment key={group.date}>
                    <TableRow className="bg-muted/50 hover:bg-muted/50">
                      <TableCell colSpan={4} className="py-1.5 text-xs font-medium tabular-nums text-muted-foreground">
                        {weekdayShort(group.date)}, {dayMonthYear(group.date)}
                      </TableCell>
                    </TableRow>
                    {group.items.map(renderRow)}
                  </Fragment>
                ))
              : shifts.map(renderRow)
            : null}
        </TableBody>
      </Table>
    </TableFrame>
  );
}
