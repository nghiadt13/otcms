"use client";

import { useEffect, useReducer, useState } from "react";
import { isApiError } from "@/lib/api/apiError";
import { notifyError, notifySuccess, notifyWarning } from "@/lib/messages/notify";
import type { LinkedProfile } from "@/lib/patient-scope/types";
import { createAppointment } from "../../api/bookingApi";
import { useBookableServices } from "../../hooks/useBookableServices";
import { useOpenTimes } from "../../hooks/useOpenTimes";
import {
  allDaysClosed,
  bookingSelectionReducer,
  bookingStepStates,
  initialBookingSelection,
  preselectedPatientId,
  toBookingPatient,
  upcomingDays,
  type BookingSelection,
} from "../../schema/bookingSelection";
import { bookingSubmitOutcome } from "../../schema/bookingSubmitOutcome";
import type {
  BookedAppointment,
  BookingPatient,
  BookingVariant,
} from "../../types/booking";
import { BookingFlowBlocks } from "./BookingFlowBlocks";

// Luồng đặt lịch dùng chung (UC20, UC21): một trang cuộn tiến dần, khối mở khi khối trước đã chọn.
// Lõi không biết người dùng là ai: người khám đến từ ngoài (khu bệnh nhân: hồ sơ đã gắn; khu Lễ
// tân: một bệnh nhân đã tìm được). `variant` chỉ đổi cỡ chữ và chạm, không đổi logic.
// File này giữ trạng thái, dữ liệu và việc gửi; markup nằm ở BookingFlowBlocks.

export type BookingFlowProps = {
  /** Gọi sau khi đặt xong (toast MSG-26 đã hiện). Trang thay luồng bằng BookingResult. */
  onBooked: (appointment: BookedAppointment) => void;
} & (
  | {
      variant: "patient";
      /** Hồ sơ đã gắn của tài khoản (trang lấy từ useLinkedProfiles). */
      profiles: readonly LinkedProfile[];
      /**
       * Người khám chọn sẵn (ví dụ từ `?profileId=`). Có trong `profiles` thì chọn sẵn; không có thì
       * bỏ qua và người dùng tự chọn như thường.
       */
      initialPatientId?: string;
    }
  | {
      variant: "staff";
      /** Bệnh nhân đã tìm được. Đổi người: đặt `key` theo `patient.id` ở nơi gọi. */
      patient: BookingPatient;
    }
);

export function BookingFlow(props: BookingFlowProps) {
  const { onBooked } = props;
  const variant: BookingVariant = props.variant;
  const patients: BookingPatient[] =
    props.variant === "patient"
      ? props.profiles.map(toBookingPatient)
      : [props.patient];
  const singlePatient = patients.length === 1 ? patients[0] : undefined;

  const [state, dispatch] = useReducer(
    bookingSelectionReducer,
    preselectedPatientId(
      props.variant === "patient" ? props.initialPatientId : undefined,
      patients,
    ),
    initialBookingSelection,
  );
  const [submitting, setSubmitting] = useState(false);
  const services = useBookableServices();

  // Khu bệnh nhân: lựa chọn của người dùng, hoặc hồ sơ duy nhất. Khu Lễ tân: người đã cố định.
  const patientId =
    props.variant === "staff"
      ? props.patient.id
      : patients.some((patient) => patient.id === state.patientId)
        ? state.patientId
        : (singlePatient?.id ?? null);
  const selectedPatient = patients.find((patient) => patient.id === patientId);

  const times = useOpenTimes(
    state.serviceId,
    patientId,
    undefined,
    handleTimesError,
  );
  const days = times.data ? upcomingDays(times.data) : null;
  const dayTimes =
    days && state.date
      ? (days.find((day) => day.date === state.date)?.times ?? [])
      : [];
  const timeAtStart = dayTimes.find((time) => time.startAt === state.startAt);
  // Giờ đã chọn biến mất sau khi tải lại (có người đặt trước): bỏ chọn, khối giờ mở lại.
  const selection: BookingSelection = {
    ...state,
    patientId,
    startAt:
      days !== null && state.startAt !== null && !timeAtStart
        ? null
        : state.startAt,
  };
  const doctorsAtTime = timeAtStart?.doctors ?? [];
  const doctorId =
    selection.doctorId !== null &&
    doctorsAtTime.some((doctor) => doctor.id === selection.doctorId)
      ? selection.doctorId
      : null;
  const doctorName =
    doctorsAtTime.find((doctor) => doctor.id === doctorId)?.fullName ?? null;
  const service = services.services.find(
    (item) => item.id === selection.serviceId,
  );
  const steps = bookingStepStates(selection, props.variant === "patient");
  const ready = Boolean(
    patientId && selection.serviceId && selection.date && selection.startAt,
  );

  // Cả dải không còn giờ: thông báo một lần cho mỗi lần có dữ liệu mới.
  useEffect(() => {
    if (times.data && allDaysClosed(upcomingDays(times.data)))
      notifyWarning("MSG-25");
  }, [times.data]);

  /** MSG-29 khi lấy giờ trống: dịch vụ hoặc người khám không đặt được, bỏ chọn dịch vụ. */
  function handleTimesError(error: unknown) {
    if (isApiError(error) && error.code === "MSG-29") {
      notifyError(error);
      dispatch({ type: "reopen", step: "service" });
    }
  }

  async function handleSubmit() {
    if (!ready || submitting || !patientId || !selection.serviceId || !selection.startAt)
      return;
    setSubmitting(true);
    try {
      const appointment = await createAppointment({
        patientId,
        serviceId: selection.serviceId,
        startAt: selection.startAt,
        doctorId,
      });
      notifySuccess("MSG-26");
      onBooked(appointment);
    } catch (error) {
      notifyError(error);
      const outcome = bookingSubmitOutcome(error);
      if (outcome === "slotTaken") {
        dispatch({ type: "reopen", step: "time" });
        times.reload();
      } else if (outcome === "doctorUnavailable") {
        dispatch({ type: "selectDoctor", doctorId: null });
        times.reload();
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (patients.length === 0)
    return (
      <p role="status" className="text-base text-muted-foreground">
        Chưa có hồ sơ bệnh nhân để đặt lịch. Hãy thêm hồ sơ trước.
      </p>
    );

  return (
    <div className="grid gap-8" inert={submitting || undefined}>
      <BookingFlowBlocks
        variant={variant}
        steps={steps}
        selection={selection}
        dispatch={dispatch}
        patients={patients}
        selectedPatient={selectedPatient}
        singlePatient={singlePatient !== undefined}
        services={services.services}
        servicesLoading={services.loading}
        servicesFailed={services.failed}
        onRetryServices={services.reload}
        service={service}
        days={days}
        timesLoading={times.loading}
        timesFailed={times.failed}
        onRetryTimes={times.reload}
        allDaysClosed={days !== null && allDaysClosed(days)}
        dayTimes={dayTimes}
        doctorsAtTime={doctorsAtTime}
        doctorId={doctorId}
        doctorName={doctorName}
        ready={ready}
        submitting={submitting}
        onSubmit={() => void handleSubmit()}
      />
    </div>
  );
}
