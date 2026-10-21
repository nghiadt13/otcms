"use client";

import { useMemo, useState, type KeyboardEvent } from "react";
import { TableFrame } from "@/components/admin/TableFrame";
import { TableStateRows } from "@/components/admin/TableStateRows";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ROLE_LABELS } from "@/lib/domain/clinicRoles";
import { cn } from "@/lib/utils";
import type { Shift, StaffOption } from "../types/shift";
import { dayMonth, isPastDate, weekdayShort } from "../utils/shiftWeekRange";
import { ShiftCell } from "./ShiftCell";

type ShiftWeekGridProps = {
  days: string[];
  /** Hàng của lưới: nhân viên đủ điều kiện, đã sắp theo vai trò rồi tên. */
  rows: StaffOption[];
  shifts: Shift[];
  today: string;
  loading: boolean;
  error: string | null;
  /** Không truyền thì ẩn "Thử lại" (lỗi 403). */
  onRetry?: () => void;
  /** Lọc đang bật: đổi lời nhắc khi không có ca. */
  filtered: boolean;
  onOpenShift: (shift: Shift) => void;
  onAddShift: (staffId: string, date: string) => void;
};

/** Lưới tuần: hàng = nhân viên, cột = ngày. Một điểm Tab, mũi tên đổi ô (roving tabindex). */
export function ShiftWeekGrid({
  days,
  rows,
  shifts,
  today,
  loading,
  error,
  onRetry,
  filtered,
  onOpenShift,
  onAddShift,
}: ShiftWeekGridProps) {
  const [active, setActive] = useState({ row: 0, col: 0 });
  const cellShifts = useMemo(() => {
    const map = new Map<string, Shift[]>();
    for (const shift of shifts) {
      const key = `${shift.staffId}|${shift.date}`;
      map.set(key, [...(map.get(key) ?? []), shift]);
    }
    for (const list of map.values())
      list.sort((a, b) => a.startTime.localeCompare(b.startTime));
    return map;
  }, [shifts]);

  const ready = !loading && !error;
  const lastRow = rows.length - 1;
  const lastCol = days.length - 1;
  // Giữ điểm Tab hợp lệ khi số hàng/cột đổi (lọc, đổi kỳ).
  const activeRow = Math.min(active.row, Math.max(lastRow, 0));
  const activeCol = Math.min(active.col, lastCol);

  function handleKeyDown(event: KeyboardEvent<HTMLTableSectionElement>) {
    const origin = (event.target as HTMLElement).closest<HTMLElement>("[data-grid-primary]");
    if (!origin) return;
    const [row, col] = origin.dataset.gridPrimary!.split(",").map(Number);
    let next: { row: number; col: number } | null = null;
    switch (event.key) {
      case "ArrowLeft": next = { row, col: Math.max(col - 1, 0) }; break;
      case "ArrowRight": next = { row, col: Math.min(col + 1, lastCol) }; break;
      case "ArrowUp": next = { row: Math.max(row - 1, 0), col }; break;
      case "ArrowDown": next = { row: Math.min(row + 1, lastRow), col }; break;
      case "Home": next = event.ctrlKey ? { row: 0, col: 0 } : { row, col: 0 }; break;
      case "End": next = event.ctrlKey ? { row: lastRow, col: lastCol } : { row, col: lastCol }; break;
    }
    if (!next) return;
    event.preventDefault();
    event.currentTarget
      .querySelector<HTMLElement>(`[data-grid-primary="${next.row},${next.col}"]`)
      ?.focus();
  }

  const noShifts = ready && rows.length > 0 && shifts.length === 0;
  const emptyText = filtered
    ? "Không có nhân viên khớp bộ lọc. Bỏ bớt lọc để xem thêm."
    : "Chưa có nhân viên nào đủ điều kiện nhận ca. Nhân viên cần tài khoản đang hoạt động và đã có hồ sơ.";

  return (
    <TableFrame>
      {noShifts ? (
        <p className="border-b px-4 py-3 text-sm text-muted-foreground">
          {filtered
            ? "Không có ca khớp bộ lọc. Bỏ bớt lọc hoặc chọn kỳ khác."
            : "Chưa có ca nào trong tuần này. Bấm ô trống hoặc Thêm ca để xếp ca đầu tiên."}
        </p>
      ) : null}
      <Table className="min-w-[56rem] table-fixed" aria-label="Lịch ca theo tuần">
        <TableHeader>
          <TableRow>
            <TableHead scope="col" className="sticky left-0 z-10 w-44 bg-card">
              Nhân viên
            </TableHead>
            {days.map((day) => (
              <TableHead
                key={day}
                scope="col"
                aria-current={day === today ? "date" : undefined}
                className={cn("text-center tabular-nums", day === today && "text-primary")}
              >
                {weekdayShort(day)} {dayMonth(day)}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody onKeyDown={handleKeyDown}>
          <TableStateRows
            colSpan={days.length + 1}
            loading={loading}
            error={error}
            onRetry={onRetry}
            empty={ready && rows.length === 0}
            emptyText={emptyText}
          />
          {ready
            ? rows.map((staff, row) => (
                <TableRow key={staff.staffId} className="align-top">
                  <TableHead
                    scope="row"
                    className="sticky left-0 z-10 h-auto bg-card py-2 font-normal whitespace-normal"
                  >
                    <span className="block font-medium text-foreground">{staff.fullName}</span>
                    <span className="block text-xs text-muted-foreground">
                      {ROLE_LABELS[staff.role]}
                    </span>
                  </TableHead>
                  {days.map((day, col) => (
                    <TableCell key={day} className="p-1 whitespace-normal">
                      <ShiftCell
                        staffId={staff.staffId}
                        staffName={staff.fullName}
                        date={day}
                        shifts={cellShifts.get(`${staff.staffId}|${day}`) ?? []}
                        past={isPastDate(day, today)}
                        row={row}
                        col={col}
                        active={row === activeRow && col === activeCol}
                        onActivate={(r, c) => setActive({ row: r, col: c })}
                        onOpenShift={onOpenShift}
                        onAddShift={onAddShift}
                      />
                    </TableCell>
                  ))}
                </TableRow>
              ))
            : null}
        </TableBody>
      </Table>
    </TableFrame>
  );
}
