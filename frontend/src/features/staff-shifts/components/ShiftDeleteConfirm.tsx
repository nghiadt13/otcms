"use client";

import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import { isApiError } from "@/lib/api/apiError";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { deleteShift } from "../api/shiftApi";
import type { Shift } from "../types/shift";
import { dayMonthYear } from "../utils/shiftWeekRange";

type ShiftDeleteConfirmProps = {
  /** Ca cần xóa; null = đóng. */
  shift: Shift | null;
  onOpenChange: (open: boolean) => void;
  /** Xóa xong hoặc ca không còn: tải lại lưới. */
  onDeleted: () => void;
  onTransfer: (staffId: string, date: string) => void;
};

/** Xác nhận xóa ca; chỉ ca bác sĩ có thêm vế "giờ nhận lịch cũng mất". */
export function ShiftDeleteConfirm({
  shift,
  onOpenChange,
  onDeleted,
  onTransfer,
}: ShiftDeleteConfirmProps) {
  const isDoctor = shift?.role === "ORTHOPEDIC_DOCTOR";

  async function handleConfirm() {
    if (!shift) return;
    try {
      await deleteShift(shift.id);
    } catch (error) {
      if (isApiError(error) && error.code === "MSG-36") {
        notifyError(error, {
          action: {
            label: "Chuyển lịch hẹn",
            onClick: () => onTransfer(shift.staffId, shift.date),
          },
        });
        throw error; // giữ dialog mở
      }
      notifyError(error);
      if (isApiError(error) && error.code === "MSG-33") {
        onDeleted(); // ca không còn: đóng và tải lại
        return;
      }
      throw error;
    }
    notifySuccess("MSG-93");
    onDeleted();
  }

  return (
    <ConfirmActionDialog
      open={shift !== null}
      onOpenChange={onOpenChange}
      title="Xóa ca làm việc?"
      description={
        shift
          ? `Ca ${shift.startTime}–${shift.endTime} ngày ${dayMonthYear(shift.date)} của ${shift.staffName} sẽ bị xóa${
              isDoctor ? ", giờ nhận lịch của bác sĩ trong ca cũng mất." : "."
            }`
          : ""
      }
      confirmLabel="Xóa ca"
      pendingLabel="Đang xóa..."
      destructive
      onConfirm={handleConfirm}
    />
  );
}
