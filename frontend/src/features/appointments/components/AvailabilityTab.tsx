"use client";

import { useEffect, useId, useRef, useState } from "react";
import { FieldError } from "@/components/admin/FieldError";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { isApiError, toFieldErrorMap } from "@/lib/api/apiError";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import { notifyError, notifyWarning } from "@/lib/messages/notify";
import type { useAppointmentUrlState } from "../hooks/useAppointmentUrlState";
import { useDoctorAvailability } from "../hooks/useDoctorAvailability";
import type { useDoctorLookup } from "../hooks/useDoctorLookup";
import { messageFromRangeError } from "../schema/appointmentMessages";
import {
  rangeErrorCode,
  resolveAvailabilityRange,
} from "../schema/appointmentUrlRules";
import { AvailabilityResult } from "./AvailabilityResult";
import { DoctorCombobox } from "./DoctorCombobox";

type Mode = "day" | "week";
type AvailabilityTabProps = {
  url: ReturnType<typeof useAppointmentUrlState>;
  lookup: ReturnType<typeof useDoctorLookup>;
};

/** Tab 2: chọn bác sĩ + ngày/tuần, "Xem giờ trống" ghi URL, lưới chỉ đọc. */
export function AvailabilityTab({ url, lookup }: AvailabilityTabProps) {
  const { state, update } = url;
  const id = useId();
  const range = resolveAvailabilityRange(state, todayIsoDate());
  // Bản nháp của điều khiển; null = theo URL.
  const [doctorEdit, setDoctorEdit] = useState<{ value?: string } | null>(null);
  const [modeEdit, setModeEdit] = useState<Mode | null>(null);
  const [fromEdit, setFromEdit] = useState<string | null>(null);
  const doctor = doctorEdit ? doctorEdit.value : state.doctorId;
  const mode: Mode = modeEdit ?? (range.from === range.to ? "day" : "week");
  const from = fromEdit ?? range.from;

  const all = lookup.doctors;
  const urlDoctor = all?.find((item) => item.id === state.doctorId);
  const notBookable = urlDoctor !== undefined && !urlDoctor.bookable;
  const rangeError = rangeErrorCode(range.from, range.to);
  const enabled =
    Boolean(state.doctorId) && !rangeError && !notBookable && !lookup.loading;
  const avail = useDoctorAvailability(
    state.doctorId,
    range.from,
    range.to,
    enabled,
  );

  // Phản ứng với kết quả: MSG-25 khi không còn khung trống; 404 thì bỏ chọn bác sĩ.
  const handled = useRef<unknown>(null);
  useEffect(() => {
    const outcome = avail.error ?? avail.data;
    if (!outcome || handled.current === outcome) return;
    handled.current = outcome;
    if (avail.data) {
      if (
        !avail.data.days.some((d) => d.slots.some((s) => s.status === "FREE"))
      )
        notifyWarning("MSG-25");
    } else if (isApiError(avail.error) && avail.error.status === 404) {
      notifyError(avail.error);
      // eslint-disable-next-line react-hooks/set-state-in-effect -- bỏ chọn bác sĩ là hệ quả của lỗi 404
      setDoctorEdit({ value: undefined });
      update({ doctorId: undefined });
    }
  }, [avail.data, avail.error, update]);

  const apiFields = isApiError(avail.error)
    ? toFieldErrorMap(avail.error.fieldErrors)
    : {};
  const doctorError = notBookable
    ? formatAppMessage("MSG-24")
    : apiFields.doctorId
      ? formatAppMessage(apiFields.doctorId)
      : undefined;
  const dateError = messageFromRangeError(
    rangeError ?? (apiFields.to === "MSG-54" ? "MSG-54" : null),
  );
  const gridError =
    avail.error &&
    !doctorError &&
    !dateError &&
    !(isApiError(avail.error) && avail.error.status === 404)
      ? messageFromError(avail.error)
      : null;
  const hasFree = Boolean(
    avail.data?.days.some((d) => d.slots.some((s) => s.status === "FREE")),
  );
  const options = (all ?? []).filter(
    (item) => item.bookable || item.id === doctor,
  );

  function apply() {
    const to = mode === "week" ? addDays(from, 6) : from;
    if (doctor === state.doctorId && from === range.from && to === range.to)
      avail.reload();
    else update({ doctorId: doctor, from, to });
  }

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-end gap-3">
        <div className="grid w-full gap-1.5 sm:w-auto">
          <Label htmlFor={`${id}-doctor`}>Bác sĩ</Label>
          <DoctorCombobox
            id={`${id}-doctor`}
            doctors={options}
            loading={lookup.loading}
            error={lookup.error}
            onRetry={lookup.reload}
            value={doctor}
            onChange={(value) => setDoctorEdit({ value })}
            placeholder="Chọn bác sĩ"
            invalid={Boolean(doctorError)}
            describedBy={doctorError ? `${id}-doctor-error` : undefined}
          />
          <FieldError id={`${id}-doctor-error`} message={doctorError} />
        </div>
        <div className="grid gap-1.5">
          <span id={`${id}-mode`} className="text-sm leading-none font-medium">
            Chế độ
          </span>
          <ToggleGroup
            variant="outline"
            aria-labelledby={`${id}-mode`}
            value={[mode]}
            onValueChange={(next) => next[0] && setModeEdit(next[0] as Mode)}
          >
            <ToggleGroupItem value="day">Ngày</ToggleGroupItem>
            <ToggleGroupItem value="week">Tuần</ToggleGroupItem>
          </ToggleGroup>
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor={`${id}-from`}>Từ ngày</Label>
          <Input
            id={`${id}-from`}
            type="date"
            className="w-40"
            value={from}
            aria-invalid={dateError ? true : undefined}
            onChange={(event) => setFromEdit(event.target.value)}
          />
          <FieldError message={dateError} />
        </div>
        <Button
          className="w-full sm:w-auto"
          disabled={!doctor || !from || avail.loading}
          onClick={apply}
        >
          {avail.loading ? "Đang tải..." : "Xem giờ trống"}
        </Button>
      </div>
      <p className="text-sm text-muted-foreground">
        Mỗi khung {avail.data?.slotMinutes ?? 15} phút. Chỉ xem, không đặt lịch
        từ màn này.
      </p>
      <AvailabilityResult
        hasDoctor={Boolean(state.doctorId)}
        loading={avail.loading}
        error={gridError}
        data={avail.data}
        hasFreeSlot={hasFree}
        onRetry={avail.reload}
      />
    </div>
  );
}
