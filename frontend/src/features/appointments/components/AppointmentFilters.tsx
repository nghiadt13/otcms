"use client";

import { DateRangeFilter } from "@/components/admin/DateRangeFilter";
import { FilterBar } from "@/components/admin/FilterBar";
import { SearchField } from "@/components/admin/SearchField";
import { Label } from "@/components/ui/label";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import { messageFromRangeError } from "../schema/appointmentMessages";
import { STATUS_VALUES } from "../schema/appointmentUrlRules";
import type { AppointmentStatus, DoctorLookupItem } from "../types/appointment";
import { STATUS_LABELS } from "./AppointmentStatusBadge";
import { DoctorCombobox } from "./DoctorCombobox";

type AppointmentFiltersProps = {
  from: string;
  to: string;
  doctorId: string | undefined;
  status: AppointmentStatus | undefined;
  q: string;
  rangeError: "MSG-54" | null;
  doctors: DoctorLookupItem[] | undefined;
  doctorsLoading: boolean;
  doctorsError: string | null;
  onDoctorsRetry: () => void;
  onRangeChange: (range: { from: string; to: string }) => void;
  onDoctorChange: (doctorId: string | undefined) => void;
  onStatusChange: (status: AppointmentStatus | undefined) => void;
  onQueryChange: (q: string) => void;
};

/** Bộ lọc tab danh sách: khoảng ngày, bác sĩ, trạng thái, tìm bệnh nhân. */
export function AppointmentFilters(props: AppointmentFiltersProps) {
  return (
    <FilterBar>
      <DateRangeFilter
        from={props.from}
        to={props.to}
        onChange={props.onRangeChange}
        error={messageFromRangeError(props.rangeError)}
      />
      <div className="grid w-full gap-1.5 sm:w-auto">
        <Label htmlFor="appointment-filter-doctor">Bác sĩ</Label>
        <DoctorCombobox
          id="appointment-filter-doctor"
          doctors={props.doctors}
          loading={props.doctorsLoading}
          error={props.doctorsError}
          onRetry={props.onDoctorsRetry}
          value={props.doctorId}
          onChange={props.onDoctorChange}
          placeholder="Chọn bác sĩ"
          allLabel="Tất cả bác sĩ"
        />
      </div>
      <div className="grid w-full gap-1.5 sm:w-auto">
        <Label htmlFor="appointment-filter-status">Trạng thái</Label>
        <NativeSelect
          id="appointment-filter-status"
          className="w-full sm:w-44"
          value={props.status ?? ""}
          onChange={(event) =>
            props.onStatusChange(
              (event.target.value || undefined) as
                AppointmentStatus | undefined,
            )
          }
        >
          <NativeSelectOption value="">Tất cả trạng thái</NativeSelectOption>
          {STATUS_VALUES.map((status) => (
            <NativeSelectOption key={status} value={status}>
              {STATUS_LABELS[status]}
            </NativeSelectOption>
          ))}
        </NativeSelect>
      </div>
      <div className="w-full sm:w-auto sm:self-end">
        <SearchField
          value={props.q}
          onChange={props.onQueryChange}
          placeholder="Tên hoặc số điện thoại"
          label="Tìm bệnh nhân theo tên hoặc số điện thoại"
        />
      </div>
    </FilterBar>
  );
}
