"use client";

import { useEffect, useReducer, useState } from "react";
import { toast } from "sonner";
import { isApiError } from "@/lib/api/apiError";
import {
  notifyError,
  notifySuccess,
  notifyWarning,
} from "@/lib/messages/notify";
import { createAppointment as createLiveAppointment } from "../../api/bookingLiveApi";
import { clearPayOsIdempotencyKeys } from "../../api/clearPayOsIdempotencyKeys";
import { useBookableServices } from "../../hooks/useBookableServices";
import { useOpenTimes } from "../../hooks/useOpenTimes";
import {
  allDaysClosed,
  bookingSelectionReducer,
  bookingStepStates,
  initialBookingSelection,
  preselectedPatientId,
  upcomingDays,
  type BookingSelection,
} from "../../schema/bookingSelection";
import {
  bookingSubmitErrorText,
  bookingSubmitOutcome,
} from "../../schema/bookingSubmitOutcome";
import type {
  BookedAppointment,
  BookingPatient,
  PaymentRenderer,
} from "../../types/booking";
import type { WizardStepKey } from "../../schema/bookingWizard";
import { PaymentHoldStep } from "./PaymentHoldStep";
import { BookingWizard } from "./wizard/BookingWizard";

// Luồng đặt lịch bệnh nhân; markup wizard nằm ở BookingWizard.

export type BookingFlowProps = {
  variant: "patient";
  patients: readonly BookingPatient[];
  renderPayment?: PaymentRenderer;
  onBooked: (appointment: BookedAppointment) => void;
};

export function BookingFlow(props: BookingFlowProps) {
  const { onBooked } = props;
  const patients: BookingPatient[] = [...props.patients];
  const singlePatient = patients.length === 1 ? patients[0] : undefined;

  const [state, dispatch] = useReducer(
    bookingSelectionReducer,
    preselectedPatientId(undefined, patients),
    initialBookingSelection,
  );
  const [submitting, setSubmitting] = useState(false);
  // Lý do khám: chỉ khu bệnh nhân nhập (ở bước xác nhận của wizard).
  const [purpose, setPurpose] = useState("");
  // Bước hiện tại của wizard bệnh nhân.
  const [wizardStep, setWizardStep] = useState<WizardStepKey>("patient");
  // Lịch vừa tạo, đang ở bước thanh toán.
  const [booked, setBooked] = useState<BookedAppointment | null>(null);
  const renderPayment = props.renderPayment;
  const services = useBookableServices(true);

  // Lựa chọn của người dùng, hoặc hồ sơ duy nhất.
  const patientId = patients.some((patient) => patient.id === state.patientId)
    ? state.patientId : (singlePatient?.id ?? null);
  const selectedPatient = patients.find((patient) => patient.id === patientId);

  const times = useOpenTimes(
    state.serviceId,
    patientId,
    undefined,
    handleTimesError,
    true,
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
  const steps = bookingStepStates(selection, true);
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
      setWizardStep("schedule");
    }
  }

  async function handleSubmit() {
    if (
      !ready ||
      submitting ||
      !patientId ||
      !selection.serviceId ||
      !selection.startAt
    )
      return;
    setSubmitting(true);
    try {
      // Đặt lịch mới: bỏ khóa idempotency PayOS còn sót để lần thanh toán này không dính attempt cũ.
      clearPayOsIdempotencyKeys();
      const appointment = await createLiveAppointment({
        patientId,
        serviceId: selection.serviceId,
        startAt: selection.startAt,
        doctorId,
        purpose,
        bookedOnline: true,
      });
      if (appointment.status === "AWAITING_PAYMENT" && renderPayment) {
        // Giữ giờ chờ thanh toán: chưa báo thành công, MSG-26 hiện sau khi trả xong ở bước 5.
        setBooked(appointment);
        setWizardStep("payment");
      } else {
        // Đã xác nhận ngay (dịch vụ miễn phí, hoặc không có bước thanh toán): bỏ qua bước 5.
        notifySuccess("MSG-26");
        onBooked(appointment);
      }
    } catch (error) {
      const text = bookingSubmitErrorText(error);
      if (text) toast.error(text);
      else notifyError(error);
      const outcome = bookingSubmitOutcome(error);
      if (outcome === "slotTaken" || outcome === "tooSoon") {
        dispatch({ type: "reopen", step: "time" });
        setWizardStep("schedule");
        times.reload();
      } else if (outcome === "doctorUnavailable") {
        dispatch({ type: "selectDoctor", doctorId: null });
        setWizardStep("schedule");
        times.reload();
      }
    } finally {
      setSubmitting(false);
    }
  }

  /** Giữ chỗ đã kết thúc (hủy / hết hạn / tab khác hủy): xóa lựa chọn và về bước đầu để đặt lại. */
  function resetAfterRelease() {
    setBooked(null);
    dispatch({ type: "reopen", step: "patient" });
    setPurpose("");
    setWizardStep("patient");
  }

  if (patients.length === 0)
    return (
      <p role="status" className="text-base text-muted-foreground">
        Chưa có hồ sơ bệnh nhân để đặt lịch. Hãy thêm hồ sơ trước.
      </p>
    );

  return (
      <div inert={submitting || undefined}>
        <BookingWizard
          step={wizardStep}
          onStepChange={setWizardStep}
          steps={steps}
          selection={selection}
          dispatch={dispatch}
          patients={patients}
          selectedPatient={selectedPatient}
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
          ready={ready && purpose.trim().length > 0}
          purpose={purpose}
          onPurposeChange={setPurpose}
          submitting={submitting}
          onSubmit={() => void handleSubmit()}
          paymentSlot={
            booked && renderPayment ? (
              <PaymentHoldStep
                appointment={booked}
                onFinish={({ paid, announced }) => {
                  if (!announced) notifySuccess("MSG-26");
                  // Kết quả hiển thị đúng trạng thái sau bước thanh toán: trả xong thì lịch đã xác nhận.
                  onBooked(
                    paid
                      ? {
                          ...booked,
                          status: "BOOKED",
                          paymentDeadline: null,
                          examFee: { ...booked.examFee, status: "PAID" },
                        }
                      : booked,
                  );
                }}
                onReleased={resetAfterRelease}
                renderPayment={renderPayment}
              />
            ) : null
          }
        />
      </div>
    );

}
