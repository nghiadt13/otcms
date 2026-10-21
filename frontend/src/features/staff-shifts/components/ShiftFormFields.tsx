"use client";

import { FieldError } from "@/components/admin/FieldError";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { ROOM_CATEGORY_LABELS, type ServiceCategory } from "@/lib/domain/clinicCategories";
import { ROLE_LABELS } from "@/lib/domain/clinicRoles";
import { describeShiftFieldError, type ShiftFormErrors, type ShiftFormValues } from "../schema/shiftFieldRules";
import type { Shift, StaffOption } from "../types/shift";
import { StaffPickerCombobox } from "./StaffPickerCombobox";

export type RoomChoice = {
  roomId: string;
  label: string;
};

type ShiftFormFieldsProps = {
  /** Sửa ca: nhân viên chỉ đọc. */
  editing: Shift | null;
  values: ShiftFormValues;
  errors: ShiftFormErrors;
  today: string;
  staff: StaffOption[];
  optionsLoading: boolean;
  optionsError: string | null;
  onRetryOptions: () => void;
  roomChoices: RoomChoice[];
  suggestedCategory: ServiceCategory | null;
  disabled: boolean;
  onChange: (patch: Partial<ShiftFormValues>) => void;
  onStaffChange: (staffId: string | undefined) => void;
  onRoomChange: (roomId: string) => void;
};

/** Các ô của dialog thêm/sửa ca; không giữ state, form cha quản lý. */
export function ShiftFormFields({
  editing,
  values,
  errors,
  today,
  staff,
  optionsLoading,
  optionsError,
  onRetryOptions,
  roomChoices,
  suggestedCategory,
  disabled,
  onChange,
  onStaffChange,
  onRoomChange,
}: ShiftFormFieldsProps) {
  const err = (field: keyof ShiftFormValues) =>
    errors[field] ? describeShiftFieldError(field, errors[field]) : null;

  return (
    <div className="grid gap-4">
      <div className="grid gap-1.5">
        <Label htmlFor="shift-staff">Nhân viên</Label>
        {editing ? (
          <p id="shift-staff" className="text-sm">
            {editing.staffName}
            <span className="text-muted-foreground"> · {ROLE_LABELS[editing.role]} (chỉ đọc)</span>
          </p>
        ) : (
          <>
            <StaffPickerCombobox
              id="shift-staff"
              staff={staff}
              value={values.staffId || undefined}
              onChange={onStaffChange}
              loading={optionsLoading}
              error={optionsError}
              onRetry={onRetryOptions}
              invalid={Boolean(errors.staffId)}
              disabled={disabled}
            />
            <FieldError message={err("staffId")} />
          </>
        )}
      </div>

      <div className="grid gap-1.5">
        <Label htmlFor="shift-date">Ngày</Label>
        <Input
          id="shift-date"
          type="date"
          min={today}
          value={values.date}
          disabled={disabled}
          aria-invalid={Boolean(errors.date)}
          onChange={(event) => onChange({ date: event.target.value })}
        />
        <FieldError message={err("date")} />
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="grid gap-1.5">
          <Label htmlFor="shift-start">Bắt đầu</Label>
          <Input
            id="shift-start"
            type="time"
            step={900}
            value={values.startTime}
            disabled={disabled}
            aria-invalid={Boolean(errors.startTime)}
            className="tabular-nums"
            onChange={(event) => onChange({ startTime: event.target.value })}
          />
          <FieldError message={err("startTime")} />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="shift-end">Kết thúc</Label>
          <Input
            id="shift-end"
            type="time"
            step={900}
            value={values.endTime}
            disabled={disabled}
            aria-invalid={Boolean(errors.endTime)}
            className="tabular-nums"
            onChange={(event) => onChange({ endTime: event.target.value })}
          />
          <FieldError message={err("endTime")} />
        </div>
      </div>

      <div className="grid gap-1.5">
        <Label htmlFor="shift-room">Phòng</Label>
        <NativeSelect
          id="shift-room"
          className="w-full"
          value={values.roomId}
          disabled={disabled}
          aria-invalid={Boolean(errors.roomId)}
          onChange={(event) => onRoomChange(event.target.value)}
        >
          <option value="">Chọn phòng</option>
          {roomChoices.map((room) => (
            <option key={room.roomId} value={room.roomId}>
              {room.label}
            </option>
          ))}
        </NativeSelect>
        {suggestedCategory ? (
          <p className="text-xs text-muted-foreground">
            Gợi ý theo vai trò: {ROOM_CATEGORY_LABELS[suggestedCategory].toLowerCase()}. Bạn có thể chọn phòng khác.
          </p>
        ) : null}
        <FieldError message={err("roomId")} />
      </div>
    </div>
  );
}
