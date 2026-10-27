"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { isApiError, toFieldErrorMap } from "@/lib/api/apiError";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { createShift, updateShift } from "../api/shiftApi";
import {
  validateShiftForm,
  type ShiftFormErrors,
  type ShiftFormField,
  type ShiftFormValues,
} from "../schema/shiftFieldRules";
import type { RoomOption, Shift, StaffOption } from "../types/shift";
import { suggestRoomCategory } from "../utils/suggestRoomCategory";
import { AffectedAppointmentsNotice } from "./AffectedAppointmentsNotice";
import { ShiftFormFields, type RoomChoice } from "./ShiftFormFields";

/** `shift` có giá trị = sửa; không có = thêm, có thể điền sẵn nhân viên và ngày. */
export type ShiftFormTarget = {
  shift?: Shift;
  staffId?: string;
  date?: string;
};

type ShiftFormDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  target: ShiftFormTarget | null;
  staff: StaffOption[];
  rooms: RoomOption[];
  optionsLoading: boolean;
  optionsError: string | null;
  onRetryOptions: () => void;
  today: string;
  /** Lưu xong hoặc ca không còn: tải lại lưới. */
  onSaved: () => void;
  onRequestDelete: (shift: Shift) => void;
  /** Mở màn Lịch hẹn lọc theo bác sĩ + ngày (BR-25). */
  onTransfer: (staffId: string, date: string) => void;
};

export function ShiftFormDialog(props: ShiftFormDialogProps) {
  const { open, onOpenChange, target } = props;
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {target?.shift ? "Sửa ca làm việc" : "Thêm ca làm việc"}
          </DialogTitle>
          <DialogDescription>
            {target?.shift
              ? "Nhân viên của ca không đổi được. Muốn đổi người, xóa ca rồi thêm ca mới."
              : "Mỗi ca gắn với một phòng. Nhân viên không được có hai ca chồng giờ."}
          </DialogDescription>
        </DialogHeader>
        {/* Form gắn/gỡ cùng dialog nên state tự đặt lại mỗi lần mở. */}
        {target ? <ShiftForm {...props} target={target} /> : null}
      </DialogContent>
    </Dialog>
  );
}

function ShiftForm({
  onOpenChange,
  target,
  staff,
  rooms,
  optionsLoading,
  optionsError,
  onRetryOptions,
  today,
  onSaved,
  onRequestDelete,
  onTransfer,
}: ShiftFormDialogProps & { target: ShiftFormTarget }) {
  const editing = target.shift ?? null;
  const [values, setValues] = useState<ShiftFormValues>({
    staffId: editing?.staffId ?? target.staffId ?? "",
    date: editing?.date ?? target.date ?? today,
    startTime: editing?.startTime ?? "",
    endTime: editing?.endTime ?? "",
    roomId: editing?.roomId ?? "",
  });
  const [errors, setErrors] = useState<ShiftFormErrors>({});
  const [roomTouched, setRoomTouched] = useState(Boolean(editing));
  const [pending, setPending] = useState(false);

  const role =
    editing?.role ?? staff.find((s) => s.staffId === values.staffId)?.role;
  const suggested = role ? suggestRoomCategory(role) : null;

  // Phòng gợi ý lên đầu; phòng đang gắn với ca nhưng không còn Sẵn sàng vẫn hiện kèm chú thích.
  const roomChoices: RoomChoice[] = [
    ...[...rooms]
      .sort(
        (a, b) =>
          Number(b.category === suggested) - Number(a.category === suggested),
      )
      .map((room) => ({ roomId: room.roomId, label: room.name })),
    ...(editing && !rooms.some((r) => r.roomId === editing.roomId)
      ? [
          {
            roomId: editing.roomId,
            label: `${editing.roomName} (Không sẵn sàng)`,
          },
        ]
      : []),
  ];

  const change = (patch: Partial<ShiftFormValues>) => {
    setValues((current) => ({ ...current, ...patch }));
    setErrors((current) => {
      const next = { ...current };
      for (const key of Object.keys(patch) as ShiftFormField[])
        delete next[key];
      return next;
    });
  };

  function changeStaff(staffId: string | undefined) {
    const nextRole = staff.find((s) => s.staffId === staffId)?.role;
    const category = nextRole ? suggestRoomCategory(nextRole) : null;
    const patch: Partial<ShiftFormValues> = { staffId: staffId ?? "" };
    if (!roomTouched)
      patch.roomId = rooms.find((r) => r.category === category)?.roomId ?? "";
    change(patch);
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const found = validateShiftForm(values, today, { requireStaff: !editing });
    setErrors(found);
    if (Object.keys(found).length > 0) return;

    setPending(true);
    try {
      if (editing) {
        const { staffId: _readonly, ...update } = values; // PUT không gửi staffId
        void _readonly;
        await updateShift(editing.id, update);
      } else {
        await createShift(values);
      }
      notifySuccess("MSG-93");
      onOpenChange(false);
      onSaved();
    } catch (error) {
      handleFailure(error);
    } finally {
      setPending(false);
    }
  }

  function handleFailure(error: unknown) {
    if (!isApiError(error)) return notifyError(error);
    if (error.code === "MSG-36") {
      // Không lưu, giữ dialog; toast dẫn tới màn Lịch hẹn để chuyển bác sĩ.
      notifyError(error, {
        action: {
          label: "Chuyển lịch hẹn",
          onClick: () => onTransfer(values.staffId, values.date),
        },
      });
      return;
    }
    notifyError(error);
    if (error.code === "MSG-33") {
      // Ca không còn, ngoài quyền hoặc đã qua: đóng và tải lại lưới.
      onOpenChange(false);
      onSaved();
      return;
    }
    const mapped = toFieldErrorMap(error.fieldErrors);
    const fields: ShiftFormErrors = {};
    for (const key of [
      "staffId",
      "date",
      "startTime",
      "endTime",
      "roomId",
    ] as const)
      if (mapped[key]) fields[key] = mapped[key];
    setErrors(fields);
  }

  return (
    <form className="grid gap-4" onSubmit={handleSubmit} noValidate>
      {editing && editing.role === "ORTHOPEDIC_DOCTOR" ? (
        <AffectedAppointmentsNotice
          shiftId={editing.id}
          onTransfer={() => onTransfer(editing.staffId, editing.date)}
        />
      ) : null}
      <ShiftFormFields
        editing={editing}
        values={values}
        errors={errors}
        today={today}
        staff={staff}
        optionsLoading={optionsLoading}
        optionsError={optionsError}
        onRetryOptions={onRetryOptions}
        roomChoices={roomChoices}
        suggestedCategory={suggested}
        disabled={pending}
        onChange={change}
        onStaffChange={changeStaff}
        onRoomChange={(roomId) => {
          setRoomTouched(true);
          change({ roomId });
        }}
      />
      <div className="flex flex-wrap items-center gap-2 border-t pt-4 sm:flex-nowrap">
        {editing?.allowedActions.includes("delete") ? (
          <Button
            type="button"
            variant="ghost"
            className="text-destructive hover:text-destructive"
            disabled={pending}
            onClick={() => onRequestDelete(editing)}
          >
            Xóa ca
          </Button>
        ) : null}
        <div className="ml-auto flex gap-2">
          <Button
            type="button"
            variant="outline"
            disabled={pending}
            onClick={() => onOpenChange(false)}
          >
            Hủy
          </Button>
          <Button type="submit" disabled={pending}>
            {pending ? "Đang lưu..." : "Lưu ca"}
          </Button>
        </div>
      </div>
    </form>
  );
}
