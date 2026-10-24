"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { FilterBar } from "@/components/admin/FilterBar";
import { SearchField } from "@/components/admin/SearchField";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import {
  APPOINTMENT_STATUS_LABELS,
  type AppointmentStatus,
} from "@/lib/domain/clinicVisit";
import { addDays } from "@/lib/format/formatDate";
import { STATUS_VALUES } from "../../schema/appointmentUrlRules";
import type { DoctorLookupItem } from "../../types/appointment";
import { DoctorCombobox } from "../DoctorCombobox";

type StaffAppointmentFiltersProps = {
  /** YYYY-MM-DD đang xem */
  date: string;
  /** Hôm nay theo giờ VN; nút "Hôm nay" bị khóa khi đã ở ngày này. */
  today: string;
  doctorId: string | undefined;
  status: AppointmentStatus | undefined;
  q: string;
  doctors: DoctorLookupItem[] | undefined;
  doctorsLoading: boolean;
  doctorsError: string | null;
  onDoctorsRetry: () => void;
  onDateChange: (date: string) => void;
  onDoctorChange: (doctorId: string | undefined) => void;
  onStatusChange: (status: AppointmentStatus | undefined) => void;
  onQueryChange: (q: string) => void;
};

/** Bộ lọc danh sách Lễ tân: ngày (‹ › Hôm nay), bác sĩ, trạng thái, tìm tên hoặc SĐT. */
export function StaffAppointmentFilters(props: StaffAppointmentFiltersProps) {
  const { date, today, onDateChange } = props;
  return (
    <FilterBar>
      <div className="grid w-full gap-1.5 sm:w-auto">
        <Label htmlFor="staff-appointment-date">Ngày khám</Label>
        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="outline"
            size="icon"
            aria-label="Ngày trước"
            onClick={() => onDateChange(addDays(date, -1))}
          >
            <ChevronLeft />
          </Button>
          <Input
            id="staff-appointment-date"
            type="date"
            value={date}
            className="min-w-0 flex-1 sm:w-44 sm:flex-none"
            onChange={(event) => {
              if (event.target.value) onDateChange(event.target.value);
            }}
          />
          <Button
            variant="outline"
            size="icon"
            aria-label="Ngày sau"
            onClick={() => onDateChange(addDays(date, 1))}
          >
            <ChevronRight />
          </Button>
          <Button
            variant="outline"
            disabled={date === today}
            onClick={() => onDateChange(today)}
          >
            Hôm nay
          </Button>
        </div>
      </div>
      <div className="grid w-full gap-1.5 sm:w-auto">
        <Label htmlFor="staff-appointment-filter-doctor">Bác sĩ</Label>
        <DoctorCombobox
          id="staff-appointment-filter-doctor"
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
        <Label htmlFor="staff-appointment-filter-status">Trạng thái</Label>
        <NativeSelect
          id="staff-appointment-filter-status"
          className="w-full sm:w-44"
          value={props.status ?? ""}
          onChange={(event) =>
            props.onStatusChange(
              (event.target.value || undefined) as AppointmentStatus | undefined,
            )
          }
        >
          <NativeSelectOption value="">Tất cả trạng thái</NativeSelectOption>
          {STATUS_VALUES.map((status) => (
            <NativeSelectOption key={status} value={status}>
              {APPOINTMENT_STATUS_LABELS[status]}
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
