"use client";

import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { messageFromError } from "@/lib/messages/appMessages";
import {
  getReceptionVerificationProfile,
  listReceptionAppointments,
  listReceptionArrivals,
  type ReceptionAppointment,
  type ReceptionArrival,
  type ReceptionCheckIn,
  type ReceptionPatient,
} from "../api/receptionLiveApi";
import { queuedFeedbackText } from "../schema/receptionMessages";
import { CheckInAppointmentRow } from "./CheckInAppointmentRow";
import { ReceptionPatientPicker } from "./ReceptionPatientPicker";
import { dateText, FormModal } from "./ReceptionUi";
import { WalkInVisitForm } from "./WalkInVisitForm";

type VerifiedPatient = {
  patientProfileId: string;
  fullName: string;
  dateOfBirth: string;
};

/**
 * Hộp thoại tiếp nhận: tìm và xác minh hồ sơ, check-in lịch hẹn trong ngày (thu phí trước nếu cần),
 * hoặc tạo lượt walk-in. `patientId`/`appointmentId` khi mở từ danh sách lịch hẹn.
 */
export function CheckInDialog({
  patientId: initialPatient,
  appointmentId: initialAppointment,
  onClose,
  onUpdated,
}: {
  patientId?: string;
  appointmentId?: string;
  onClose: () => void;
  onUpdated: () => void;
}) {
  const [patientId, setPatientId] = useState(initialPatient ?? "");
  const [selected, setSelected] = useState<ReceptionPatient | null>(null);
  const [appointments, setAppointments] = useState<ReceptionAppointment[]>([]);
  const [appointmentsPatientId, setAppointmentsPatientId] = useState("");
  const [arrivals, setArrivals] = useState<ReceptionArrival[]>([]);
  const [verified, setVerified] = useState<VerifiedPatient | null>(null);
  const [verifying, setVerifying] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [error, setError] = useState("");
  const [result, setResult] = useState("");
  const today = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh",
  }).format(new Date());

  useEffect(() => {
    if (!patientId) return;
    const controller = new AbortController();
    void listReceptionAppointments(
      { from: today, to: today, patientProfileId: patientId },
      controller.signal,
    )
      .then((page) => {
        setAppointments(
          page.items.filter((item) =>
            ["CONFIRMED", "NO_SHOW"].includes(item.status),
          ),
        );
        setAppointmentsPatientId(patientId);
      })
      .catch((cause) => {
        if (!controller.signal.aborted) setError(messageFromError(cause));
      });
    return () => controller.abort();
  }, [patientId, today, reloadKey]);

  useEffect(() => {
    const controller = new AbortController();
    void listReceptionArrivals({ date: today }, controller.signal)
      .then((worklist) => setArrivals(worklist.items))
      .catch((cause) => {
        if (!controller.signal.aborted) setError(messageFromError(cause));
      });
    return () => controller.abort();
  }, [today]);

  async function verifyIdentity() {
    if (!patientId || verifying) return;
    setVerifying(true);
    setError("");
    try {
      setVerified(await getReceptionVerificationProfile(patientId));
    } catch (cause) {
      setError(messageFromError(cause));
    } finally {
      setVerifying(false);
    }
  }

  function pickPatient(patient: ReceptionPatient) {
    setSelected(patient);
    setPatientId(patient.patientProfileId);
    setVerified(null);
    setError("");
  }

  function resetPatient() {
    setSelected(null);
    setPatientId("");
    setVerified(null);
  }

  function handleCheckedIn(appointmentId: string, checkIn: ReceptionCheckIn) {
    toast.success(queuedFeedbackText(checkIn.queueNumber, checkIn.roomLabel));
    setResult(`Bác sĩ phụ trách: ${checkIn.doctorName}.`);
    setAppointments((items) =>
      items.filter((item) => item.id !== appointmentId),
    );
    onUpdated();
  }

  function handleArrivalRecorded(arrival: ReceptionArrival) {
    setArrivals((items) => [
      ...items.filter((item) => item.appointmentId !== arrival.appointmentId),
      arrival,
    ]);
  }

  const visibleAppointments = appointments.filter(
    (appointment) =>
      !initialAppointment || appointment.id === initialAppointment,
  );
  // Walk-in khi không mở từ một lịch hẹn cụ thể, hoặc khi lịch đó đã chuyển sang không đến.
  const walkInAllowed =
    !initialAppointment ||
    appointments.some(
      (appointment) =>
        appointment.id === initialAppointment &&
        appointment.status === "NO_SHOW",
    );

  return (
    <FormModal
      title="Tiếp nhận bệnh nhân"
      description="Tìm hồ sơ, đối chiếu thông tin bệnh nhân, rồi chọn lịch hẹn hoặc tiếp nhận trực tiếp."
      onClose={onClose}
      wide
    >
      <form className="space-y-5" onSubmit={(event) => event.preventDefault()}>
        {initialAppointment && (
          <p className="text-sm">Lịch hẹn: {initialAppointment}</p>
        )}
        {!initialPatient && (
          <ReceptionPatientPicker
            onPick={pickPatient}
            onTyping={resetPatient}
          />
        )}
        {selected && (
          <p className="text-sm">
            Đã chọn: {selected.fullName} · {dateText(selected.dateOfBirth)}
          </p>
        )}
        {initialPatient && (
          <p className="text-xs text-muted-foreground">Mã hồ sơ: {patientId}</p>
        )}
        {!verified ? (
          <Button
            type="button"
            disabled={!patientId || verifying}
            onClick={() => void verifyIdentity()}
          >
            {verifying ? "Đang xác minh…" : "Xác minh hồ sơ bệnh nhân"}
          </Button>
        ) : (
          <>
            <p className="rounded-md bg-muted p-3 text-sm">
              Đã xác minh hồ sơ: {verified.fullName} ·{" "}
              {dateText(verified.dateOfBirth)}.
            </p>
            {appointmentsPatientId === verified.patientProfileId &&
              visibleAppointments.map((appointment) => (
                <CheckInAppointmentRow
                  key={appointment.id}
                  appointment={appointment}
                  arrival={arrivals.find(
                    (arrival) => arrival.appointmentId === appointment.id,
                  )}
                  verifiedPatientProfileId={verified.patientProfileId}
                  onCheckedIn={(checkIn) =>
                    handleCheckedIn(appointment.id, checkIn)
                  }
                  onArrivalRecorded={handleArrivalRecorded}
                  onFeePaid={() => setReloadKey((value) => value + 1)}
                />
              ))}
            {walkInAllowed && (
              <WalkInVisitForm
                key={verified.patientProfileId}
                patientProfileId={verified.patientProfileId}
                priorAppointmentId={initialAppointment}
              />
            )}
          </>
        )}
        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
        {result && (
          <p role="status" className="text-sm">
            {result}
          </p>
        )}
        <div className="flex justify-end">
          <Button type="button" variant="outline" onClick={onClose}>
            Đóng
          </Button>
        </div>
      </form>
    </FormModal>
  );
}
