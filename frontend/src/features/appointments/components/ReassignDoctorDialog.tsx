"use client";

import { useEffect, useRef, useState } from "react";
import { DetailList } from "@/components/admin/DetailList";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { isApiError, toFieldErrorMap } from "@/lib/api/apiError";
import { formatDateTime } from "@/lib/format/formatDate";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { reassignAppointmentDoctor } from "../api/appointmentApi";
import { useEligibleDoctors } from "../hooks/useEligibleDoctors";
import { reassignSuccessText } from "../schema/appointmentMessages";
import type { Appointment } from "../types/appointment";
import { ReassignCandidateList } from "./ReassignCandidateList";

type ReassignDoctorDialogProps = {
  appointment: Appointment | null;
  onClose: () => void;
  /** Chuyển thành công: bản ghi mới từ BE. */
  onChanged: (updated: Appointment) => void;
  /** Lịch không còn / không còn Đã đặt: màn đóng Dialog và tải lại. */
  onStale: () => void;
  /** AF1: màn đóng Dialog và mở xác nhận gửi thông báo. */
  onNotifyRequest: (appointment: Appointment) => void;
};

/** 404/403 hoặc 409 MSG-32: lịch không còn dùng được, phải đóng và tải lại. */
export function isStaleAppointmentError(error: unknown): boolean {
  return (
    isApiError(error) &&
    (error.status === 404 ||
      error.status === 403 ||
      (error.status === 409 && error.code === "MSG-32"))
  );
}

export function ReassignDoctorDialog({
  appointment,
  ...handlers
}: ReassignDoctorDialogProps) {
  return (
    <Dialog
      open={appointment !== null}
      onOpenChange={(open) => !open && handlers.onClose()}
    >
      <DialogContent className="sm:max-w-md">
        {appointment ? (
          <ReassignBody key={appointment.id} appointment={appointment} {...handlers} />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function ReassignBody({
  appointment,
  onClose,
  onChanged,
  onStale,
  onNotifyRequest,
}: Omit<ReassignDoctorDialogProps, "appointment"> & {
  appointment: Appointment;
}) {
  const eligible = useEligibleDoctors(appointment.id);
  const [selectedId, setSelectedId] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [fieldError, setFieldError] = useState<string | undefined>();
  const handledError = useRef<unknown>(null);

  // Lịch không còn dùng được khi tải ứng viên: toast, đóng, tải lại.
  useEffect(() => {
    const error = eligible.error;
    if (!error || handledError.current === error || !isStaleAppointmentError(error))
      return;
    handledError.current = error;
    notifyError(error);
    onStale();
  }, [eligible.error, onStale]);

  const loadError =
    eligible.error && !isStaleAppointmentError(eligible.error)
      ? messageFromError(eligible.error)
      : null;
  const noCandidates = !eligible.loading && !loadError && eligible.doctors?.length === 0;
  const canNotify = noCandidates && appointment.allowedActions.includes("notifyNoReplacement");

  async function submit() {
    setSubmitting(true);
    setFieldError(undefined);
    try {
      const updated = await reassignAppointmentDoctor(appointment.id, selectedId);
      notifySuccess(reassignSuccessText(updated.doctor?.fullName ?? ""));
      onChanged(updated);
    } catch (error) {
      if (isStaleAppointmentError(error)) {
        notifyError(error);
        onStale();
      } else if (isApiError(error) && error.status === 409 && error.code === "MSG-24") {
        // Bác sĩ vừa chọn không còn khả dụng: giữ Dialog, chọn lại từ danh sách mới.
        notifyError(error);
        setSelectedId("");
        eligible.reload();
      } else {
        const doctorCode = isApiError(error)
          ? toFieldErrorMap(error.fieldErrors).doctorId
          : undefined;
        if (doctorCode) setFieldError(formatAppMessage(doctorCode));
        else notifyError(error);
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Chuyển bác sĩ</DialogTitle>
        <DialogDescription>
          Chỉ chuyển sang bác sĩ khác có ca cùng giờ. Giờ hẹn không thay đổi.
        </DialogDescription>
      </DialogHeader>
      <DetailList
        items={[
          { label: "Bệnh nhân", value: appointment.patientName },
          {
            label: "Giờ hẹn",
            value: (
              <span className="tabular-nums">{formatDateTime(appointment.startAt)}</span>
            ),
          },
          {
            label: "Bác sĩ hiện tại",
            value: appointment.doctor?.fullName ?? "Chưa chỉ định",
          },
        ]}
      />
      <ReassignCandidateList
        loading={eligible.loading}
        error={loadError}
        doctors={eligible.doctors}
        selectedId={selectedId}
        onSelect={setSelectedId}
        fieldError={fieldError}
        disabled={submitting}
        onRetry={eligible.reload}
      />
      <DialogFooter>
        <Button variant="outline" disabled={submitting} onClick={onClose}>
          Hủy
        </Button>
        {canNotify ? (
          <Button onClick={() => onNotifyRequest(appointment)}>
            Báo cho bệnh nhân
          </Button>
        ) : null}
        {!noCandidates && !loadError ? (
          <Button
            disabled={eligible.loading || !selectedId || submitting}
            onClick={() => void submit()}
          >
            {submitting ? "Đang chuyển..." : "Chuyển lịch hẹn"}
          </Button>
        ) : null}
      </DialogFooter>
    </>
  );
}
