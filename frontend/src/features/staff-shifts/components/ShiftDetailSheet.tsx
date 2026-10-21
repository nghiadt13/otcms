"use client";

import { DetailList } from "@/components/admin/DetailList";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { ROOM_CATEGORY_LABELS } from "@/lib/domain/clinicCategories";
import { ROLE_LABELS } from "@/lib/domain/clinicRoles";
import type { Shift } from "../types/shift";
import { dayMonthYear } from "../utils/shiftWeekRange";

type ShiftDetailSheetProps = {
  /** Ca đang xem; null = đóng. */
  shift: Shift | null;
  onOpenChange: (open: boolean) => void;
};

/** Chi tiết ca đã qua hoặc đã bắt đầu: chỉ xem, không sửa/xóa. */
export function ShiftDetailSheet({ shift, onOpenChange }: ShiftDetailSheetProps) {
  return (
    <Sheet open={shift !== null} onOpenChange={onOpenChange}>
      <SheetContent className="w-full data-[side=right]:sm:max-w-md">
        <SheetHeader>
          <SheetTitle>Ca đã qua, chỉ xem</SheetTitle>
          <SheetDescription>Ca đã qua hoặc đã bắt đầu không sửa hay xóa được.</SheetDescription>
        </SheetHeader>
        {shift ? (
          <div className="px-4">
            <DetailList
              items={[
                { label: "Nhân viên", value: `${shift.staffName} · ${ROLE_LABELS[shift.role]}` },
                { label: "Ngày", value: <span className="tabular-nums">{dayMonthYear(shift.date)}</span> },
                { label: "Giờ", value: <span className="tabular-nums">{shift.startTime}–{shift.endTime}</span> },
                { label: "Phòng", value: `${shift.roomName} · ${ROOM_CATEGORY_LABELS[shift.roomCategory]}` },
              ]}
            />
          </div>
        ) : null}
        <div className="mt-auto flex justify-end p-4">
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Đóng
          </Button>
        </div>
      </SheetContent>
    </Sheet>
  );
}
