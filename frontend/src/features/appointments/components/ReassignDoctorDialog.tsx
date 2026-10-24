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
import { useEligibleDoctors } from "../hooks/useEligibleDoctors";
import { reassignSuccessText } from "../schema/appointmentMessages";
import { isStaleAppointmentError } from "../schema/staleAppointmentError";
import type {
  ReassignDoctorApi,
  ReassignTarget,
} from "../types/reassignDoctorApi";
import { ReassignCandidateList } from "./ReassignCandidateList";

// Dialog chuyển bác sĩ dùng chung admin và Lễ tân. Mọi lời gọi đi qua `api` truyền vào; Dialog
// không biết khu nào đang gọi.

type ReassignDoctorDialogProps<T extends ReassignTarget> = {
  appointment: T | null;
  /** Bộ endpoint của khu đang đứng: admin truyền bộ /admin, Lễ tân truyền bộ /appointments. */
  api: ReassignDoctorApi<T>;
  /** Tên người khám hiện trong Dialog (mỗi khu có kiểu lịch khác nhau). */
  patientNameOf: (appointment: T) => string;
  /** Dòng giải thích khi không có bác sĩ thay (AF1). Không truyền thì dùng câu của khu admin. */
  noCandidateLines?: readonly string[];
  onClose: () => void;
  /** Chuyển thành công: bản ghi mới từ BE. */
  onChanged: (updated: T) => void;
  /** Lịch không còn / không còn Đã đặt: màn đóng Dialog và tải lại. */
  onStale: () => void;
  /** AF1: màn đóng Dialog và mở xác nhận gửi thông báo. */
  onNotifyRequest: (appointment: T) => void;
};

type ReassignBodyProps<T extends ReassignTarget> = Omit<
  ReassignDoctorDialogProps<T>,
  "appointment"
> & { appointment: T };

export function ReassignDoctorDialog<T extends ReassignTarget>(
  props: ReassignDoctorDialogProps<T>,
) {
  const { appointment, onClose } = props;
  return (
    <Dialog
      open={appointment !== null}
      onOpenChange={(open) => !open && onClose()}
    >
      <DialogContent className="sm:max-w-md">
        {appointment ? (
          <ReassignBody {...props} key={appointment.id} appointment={appointment} />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function ReassignBody<T extends ReassignTarget>({
  appointment,
  api,
  patientNameOf,
  noCandidateLines,
  onClose,
  onChanged,
  onStale,
  onNotifyRequest,
}: ReassignBodyProps<T>) {
  const eligible = useEligibleDoctors(appointment.id, api);
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
      const updated = await api.reassign(appointment.id, selectedId);
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
          { label: "Bệnh nhân", value: patientNameOf(appointment) },
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
        noCandidateLines={noCandidateLines}
      />
      <DialogFooter>
        <Button variant="outline" disabled={submitting} onClick={onClose}>
          Hủy
        </Button>
        {canNotify ? (
          <Button onClick={() => onNotifyRequest(appointment)}>Báo cho bệnh nhân</Button>
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
