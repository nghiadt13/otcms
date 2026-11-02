"use client";

import type { Dispatch } from "react";
import type { DoctorRef } from "../../types/appointment";
import {
  groupTimesByPeriod,
  type BookingSelection,
  type BookingSelectionAction,
  type BookingStepStates,
} from "../../schema/bookingSelection";
import {
  dateSummaryOf,
  patientLabelOf,
  serviceSummaryOf,
  timeSummaryOf,
} from "../../schema/bookingLabels";
import type {
  BookableService,
  BookingPatient,
  BookingVariant,
  OpenDay,
  OpenTime,
} from "../../types/booking";
import { BookingPatientChoice } from "./BookingPatientChoice";
import { BookingStep } from "./BookingStep";
import { BookingSummary } from "./BookingSummary";
import { DateStrip } from "./DateStrip";
import { DoctorChoice } from "./DoctorChoice";
import { ServicePicker } from "./ServicePicker";
import { TimeSlotGrid } from "./TimeSlotGrid";

// Các khối của luồng đặt lịch, theo đúng thứ tự đọc: Ai đi khám → Dịch vụ → Ngày → Giờ → Bác sĩ →
// Kiểm tra lại. Khối nào hiện, mở hay đã chọn do `steps` quyết định; file này chỉ vẽ.

export interface BookingFlowBlocksProps {
  variant: BookingVariant;
  steps: BookingStepStates;
  selection: BookingSelection;
  dispatch: Dispatch<BookingSelectionAction>;
  /** Người khám đã áp dụng (khu bệnh nhân: hồ sơ đã chọn hoặc hồ sơ duy nhất). */
  patients: readonly BookingPatient[];
  selectedPatient: BookingPatient | undefined;
  /** Chỉ có một hồ sơ: không hiện "Đổi" ở khối người khám. */
  singlePatient: boolean;
  services: readonly BookableService[];
  servicesLoading: boolean;
  servicesFailed: boolean;
  onRetryServices: () => void;
  service: BookableService | undefined;
  /** Ngày đã lọc giờ đã qua; null khi chưa có dữ liệu. */
  days: readonly OpenDay[] | null;
  timesLoading: boolean;
  timesFailed: boolean;
  onRetryTimes: () => void;
  allDaysClosed: boolean;
  /** Giờ trong ngày đã chọn. */
  dayTimes: readonly OpenTime[];
  doctorsAtTime: readonly DoctorRef[];
  /** Bác sĩ đang chọn, đã kiểm còn ở giờ này; null = phòng khám sắp xếp. */
  doctorId: string | null;
  doctorName: string | null;
  ready: boolean;
  submitting: boolean;
  onSubmit: () => void;
}

export function BookingFlowBlocks(props: BookingFlowBlocksProps) {
  const {
    variant,
    steps,
    selection,
    dispatch,
    patients,
    selectedPatient,
    singlePatient,
    service,
    days,
    dayTimes,
    doctorsAtTime,
    doctorId,
    doctorName,
    ready,
    submitting,
  } = props;
  const groups = groupTimesByPeriod(dayTimes);
  const doctorIdsAt = (startAt: string) =>
    dayTimes
      .find((time) => time.startAt === startAt)
      ?.doctors.map((doctor) => doctor.id) ?? [];

  return (
    <>
      {steps.patient !== "hidden" ? (
        <BookingStep
          title="Ai đi khám?"
          variant={variant}
          open={steps.patient === "open"}
          summary={
            selectedPatient ? patientLabelOf(selectedPatient) : undefined
          }
          onChange={
            singlePatient
              ? undefined
              : () => dispatch({ type: "reopen", step: "patient" })
          }
        >
          <BookingPatientChoice
            patients={patients}
            value={selection.patientId}
            onChange={(patientId) =>
              dispatch({ type: "selectPatient", patientId })
            }
            variant={variant}
            label="Ai đi khám?"
          />
        </BookingStep>
      ) : null}

      {steps.service !== "hidden" ? (
        <BookingStep
          title="Khám dịch vụ gì?"
          variant={variant}
          open={steps.service === "open"}
          summary={service ? serviceSummaryOf(service) : undefined}
          onChange={() => dispatch({ type: "reopen", step: "service" })}
        >
          <ServicePicker
            services={props.services}
            loading={props.servicesLoading}
            failed={props.servicesFailed}
            value={selection.serviceId}
            onChange={(serviceId) =>
              dispatch({ type: "selectService", serviceId })
            }
            onRetry={props.onRetryServices}
            variant={variant}
            label="Dịch vụ khám"
          />
        </BookingStep>
      ) : null}

      {steps.date !== "hidden" ? (
        <BookingStep
          title="Khám ngày nào?"
          variant={variant}
          open={steps.date === "open"}
          summary={selection.date ? dateSummaryOf(selection.date) : undefined}
          onChange={() => dispatch({ type: "reopen", step: "date" })}
        >
          <DateStrip
            days={days}
            loading={props.timesLoading}
            failed={props.timesFailed}
            selected={selection.date}
            onSelect={(date) => dispatch({ type: "selectDate", date })}
            onRetry={props.onRetryTimes}
            allClosed={props.allDaysClosed}
            variant={variant}
            label="Ngày khám"
          />
        </BookingStep>
      ) : null}

      {steps.time !== "hidden" ? (
        <BookingStep
          title="Khám lúc mấy giờ?"
          variant={variant}
          open={steps.time === "open"}
          summary={
            selection.startAt ? timeSummaryOf(selection.startAt) : undefined
          }
          onChange={() => dispatch({ type: "reopen", step: "time" })}
        >
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
            variant={variant}
            label="Giờ khám"
          />
        </BookingStep>
      ) : null}

      {steps.doctor !== "hidden" ? (
        <BookingStep title="Muốn khám với bác sĩ nào?" variant={variant} open>
          <DoctorChoice
            doctors={doctorsAtTime}
            value={doctorId}
            onChange={(id) => dispatch({ type: "selectDoctor", doctorId: id })}
            variant={variant}
            label="Bác sĩ khám"
          />
        </BookingStep>
      ) : null}

      {steps.summary && service ? (
        <BookingStep title="Kiểm tra lại" variant={variant} open>
          <BookingSummary
            variant={variant}
            patientLabel={
              selectedPatient ? patientLabelOf(selectedPatient) : ""
            }
            serviceName={service.name}
            price={service.price}
            startAt={selection.startAt}
            doctorName={doctorName}
            ready={ready}
            submitting={submitting}
            onSubmit={props.onSubmit}
          />
        </BookingStep>
      ) : null}
    </>
  );
}
