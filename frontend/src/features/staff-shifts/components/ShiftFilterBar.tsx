"use client";

import { FilterBar } from "@/components/admin/FilterBar";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import {
  ROLE_LABELS,
  STAFF_ROLES,
  type StaffRole,
} from "@/lib/domain/clinicRoles";
import type { ShiftFilters } from "../hooks/useShiftWeek";
import type { RoomOption, StaffOption } from "../types/shift";
import { StaffPickerCombobox } from "./StaffPickerCombobox";

type ShiftFilterBarProps = {
  filters: ShiftFilters;
  staff: StaffOption[];
  rooms: RoomOption[];
  optionsLoading: boolean;
  optionsError: string | null;
  onRetryOptions: () => void;
  onChange: (patch: ShiftFilters) => void;
};

/** Lọc theo nhân viên, vai trò, phòng. Đổi vai trò thì bỏ nhân viên khác vai trò. */
export function ShiftFilterBar({
  filters,
  staff,
  rooms,
  optionsLoading,
  optionsError,
  onRetryOptions,
  onChange,
}: ShiftFilterBarProps) {
  const selectedStaff = staff.find((s) => s.staffId === filters.staffId);

  function changeRole(value: string) {
    const role = (value || undefined) as StaffRole | undefined;
    const keepStaff = !role || !selectedStaff || selectedStaff.role === role;
    onChange(keepStaff ? { role } : { role, staffId: undefined });
  }

  return (
    <FilterBar>
      <div className="grid w-full gap-1 sm:w-auto">
        <Label
          htmlFor="shift-filter-staff"
          className="text-xs text-muted-foreground"
        >
          Nhân viên
        </Label>
        <StaffPickerCombobox
          id="shift-filter-staff"
          allowAll
          staff={
            filters.role ? staff.filter((s) => s.role === filters.role) : staff
          }
          value={filters.staffId}
          onChange={(staffId) => onChange({ staffId })}
          loading={optionsLoading}
          error={optionsError}
          onRetry={onRetryOptions}
        />
      </div>
      <div className="grid w-full gap-1 sm:w-auto">
        <Label
          htmlFor="shift-filter-role"
          className="text-xs text-muted-foreground"
        >
          Vai trò
        </Label>
        <NativeSelect
          id="shift-filter-role"
          className="w-full sm:w-52"
          value={filters.role ?? ""}
          onChange={(event) => changeRole(event.target.value)}
        >
          <option value="">Tất cả vai trò</option>
          {STAFF_ROLES.map((role) => (
            <option key={role} value={role}>
              {ROLE_LABELS[role]}
            </option>
          ))}
        </NativeSelect>
      </div>
      <div className="grid w-full gap-1 sm:w-auto">
        <Label
          htmlFor="shift-filter-room"
          className="text-xs text-muted-foreground"
        >
          Phòng
        </Label>
        <NativeSelect
          id="shift-filter-room"
          className="w-full sm:w-52"
          value={filters.roomId ?? ""}
          onChange={(event) =>
            onChange({ roomId: event.target.value || undefined })
          }
        >
          <option value="">Tất cả phòng</option>
          {rooms.map((room) => (
            <option key={room.roomId} value={room.roomId}>
              {room.name}
            </option>
          ))}
        </NativeSelect>
      </div>
    </FilterBar>
  );
}
