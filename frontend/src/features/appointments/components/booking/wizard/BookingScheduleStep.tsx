"use client";

import type { ReactNode } from "react";
import {
  groupTimesByPeriod,
  type BookingSelection,
  type BookingSelectionAction,
  type BookingStepStates,
} from "../../../schema/bookingSelection";
import type { DoctorRef } from "../../../types/appointment";
import type {
  BookableService,
  OpenDay,
  OpenTime,
} from "../../../types/booking";
import { DateStrip } from "../DateStrip";
import { DoctorChoice } from "../DoctorChoice";
import { ServicePicker } from "../ServicePicker";
import { TimeSlotGrid } from "../TimeSlotGrid";
import type { Dispatch } from "react";

// Bước 3: loại khám, ngày, giờ và bác sĩ trong một màn. Các phần hiện dần theo `steps` (ngày sau
// khi chọn dịch vụ, giờ sau khi chọn ngày, bác sĩ sau khi chọn giờ) để không ngợp.

export interface BookingScheduleStepProps {
  steps: BookingStepStates;
  selection: BookingSelection;
  dispatch: Dispatch<BookingSelectionAction>;
  services: readonly BookableService[];
  servicesLoading: boolean;
  servicesFailed: boolean;
  onRetryServices: () => void;
  days: readonly OpenDay[] | null;
  timesLoading: boolean;
  timesFailed: boolean;
  onRetryTimes: () => void;
  allDaysClosed: boolean;
  dayTimes: readonly OpenTime[];
  doctorsAtTime: readonly DoctorRef[];
  doctorId: string | null;
}

function Part({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="grid grid-cols-[minmax(0,1fr)] gap-3 border-t pt-6 first:border-t-0 first:pt-0 motion-safe:animate-in motion-safe:fade-in motion-safe:duration-200">
      <h3 className="text-base font-semibold">{title}</h3>
      {children}
    </section>
  );
}

export function BookingScheduleStep(props: BookingScheduleStepProps) {
  const { steps, selection, dispatch, dayTimes } = props;
  const groups = groupTimesByPeriod(dayTimes);
  const doctorIdsAt = (startAt: string) =>
    dayTimes
      .find((time) => time.startAt === startAt)
      ?.doctors.map((doctor) => doctor.id) ?? [];

  return (
    <div className="grid grid-cols-[minmax(0,1fr)] gap-6">
      <Part title="Loại khám">
        <ServicePicker
          services={props.services}
          loading={props.servicesLoading}
          failed={props.servicesFailed}
          value={selection.serviceId}
          onChange={(serviceId) =>
            dispatch({ type: "selectService", serviceId })
          }
          onRetry={props.onRetryServices}
          variant="patient"
          label="Dịch vụ khám"
        />
      </Part>

      {steps.date !== "hidden" ? (
        <Part title="Ngày khám">
          <DateStrip
            days={props.days}
            loading={props.timesLoading}
            failed={props.timesFailed}
            selected={selection.date}
            onSelect={(date) => dispatch({ type: "selectDate", date })}
            onRetry={props.onRetryTimes}
            allClosed={props.allDaysClosed}
            variant="patient"
            label="Ngày khám"
          />
        </Part>
      ) : null}

      {steps.time !== "hidden" ? (
        <Part title="Giờ khám">
          <TimeSlotGrid
            morning={groups.morning}
            afternoon={groups.afternoon}
            loading={props.timesLoading}
            dayClosed={dayTimes.length === 0}
            value={selection.startAt}
            onChange={(startAt) =>
              dispatch({
                type: "selectTime",
                startAt,
                doctorIdsAtTime: doctorIdsAt(startAt),
              })
            }
            variant="patient"
            label="Giờ khám"
          />
        </Part>
      ) : null}

      {steps.doctor !== "hidden" ? (
        <Part title="Bác sĩ khám">
          <DoctorChoice
            doctors={props.doctorsAtTime}
            value={props.doctorId}
            onChange={(id) => dispatch({ type: "selectDoctor", doctorId: id })}
            variant="patient"
            label="Bác sĩ khám"
          />
        </Part>
      ) : null}
    </div>
  );
}
