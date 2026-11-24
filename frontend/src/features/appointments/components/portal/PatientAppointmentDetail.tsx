"use client";

import Link from "next/link";
import { notFound } from "next/navigation";
import { useCallback, useState, type ReactNode } from "react";
import { ChevronLeft } from "lucide-react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Skeleton } from "@/components/ui/skeleton";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import { isApiError } from "@/lib/api/apiError";
import {
  APPOINTMENT_STATUS_LABELS,
  APPOINTMENT_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
import { todayIsoDate } from "@/lib/format/formatDate";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import {
  getMyRescheduleSlots,
  rescheduleMyAppointment,
} from "../../api/patientAppointmentSource";
import { usePatientAppointment } from "../../hooks/usePatientAppointment";
import { usePatientExamQueueEntry } from "../../hooks/usePatientExamQueueEntry";
import { dateOfStart } from "../../schema/bookingTime";
import type { PatientAppointment } from "../../types/patientAppointment";
import { RescheduleAppointmentDialog } from "../RescheduleAppointmentDialog";
import { PatientAppointmentActions } from "./PatientAppointmentActions";
import { PatientArrivalQrCheckIn } from "./PatientArrivalQrCheckIn";
import { PatientAppointmentFacts } from "./PatientAppointmentFacts";
import { PatientCancelDialog } from "./PatientCancelDialog";
import { PatientExamQueueStatus } from "./PatientExamQueueStatus";
import { PatientLoadError } from "./PatientLoadError";
import {
  appointmentDetailTitle,
  arrivalNote,
  refundNotice,
} from "./patientAppointmentPresentation";

// Chi tiết một lịch hẹn (UC23, UC24). Lịch không có hoặc ngoài phạm vi (404/403 MSG-29) đi tới
// trang không tìm thấy của khu, không lộ lý do. Mọi nút theo `allowedActions` của BE.

export function PatientAppointmentDetail({ id }: { id: string }) {
  const { appointment, loadedAt, loading, error, reload, replace } =
    usePatientAppointment(id);
  const { profiles } = useLinkedProfiles();
  const queue = usePatientExamQueueEntry(appointment?.visitId ?? null);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [rescheduleOpen, setRescheduleOpen] = useState(false);
  const loadRescheduleSlots = useCallback(
    (date: string, signal: AbortSignal) =>
      getMyRescheduleSlots(id, date, signal),
    [id],
  );

  if (error && isApiError(error) && error.code === "MSG-29") notFound();

  if (loading)
    return (
      <DetailFrame title="Chi tiết lịch hẹn">
        <DetailSkeleton />
      </DetailFrame>
    );
  if (error || !appointment)
    return (
      <DetailFrame title="Chi tiết lịch hẹn">
        <PatientLoadError message={messageFromError(error)} onRetry={reload} />
      </DetailFrame>
    );

  const patientCode = profiles.find(
    (profile) => profile.id === appointment.patient.id,
  )?.patientCode;
  // Mốc tính theo lúc tải lịch (`loadedAt`, cùng lúc BE tính allowedActions): đã tới giờ chưa, và có
  // phải ngày hôm nay (giờ VN) không.
  const started = Date.parse(appointment.startAt) <= loadedAt;
  const paid = appointment.examFee.status === "PAID";
  const feeSettled = paid || appointment.examFee.status === "NOT_REQUIRED";
  const isToday =
    dateOfStart(appointment.startAt) === todayIsoDate(new Date(loadedAt));
  // Check-in QR chỉ trong ngày hẹn, khi phí đã xong (A7); trong ngày không có khung giờ.
  const canArriveByQr =
    appointment.status === "BOOKED" && isToday && feeSettled;
  // Còn dưới hạn tự hủy (lịch chưa thanh toán): không có nút hủy, nhắc bằng MSG-32 (plan B1).
  const cancelWindowClosed =
    appointment.status === "BOOKED" &&
    !started &&
    !paid &&
    !appointment.allowedActions.includes("cancel");
  const maxReschedules = appointment.maxPatientReschedules ?? 2;
  const remainingReschedules =
    appointment.remainingReschedules ??
    Math.max(0, maxReschedules - (appointment.patientRescheduleCount ?? 0));
  const canSelfReschedule = appointment.allowedActions.includes("reschedule");

  return (
    <DetailFrame
      title={appointmentDetailTitle(appointment.startAt)}
      status={appointment}
    >
      {appointment.doctorUnavailable && appointment.status === "BOOKED" ? (
        <Alert variant="destructive">
          <AlertDescription className="text-base">
            Bác sĩ của lịch hẹn này không thể khám và chưa có bác sĩ thay thế.
            Bạn có thể hủy lịch hẹn.
            {appointment.examFee.status === "PAID"
              ? ` ${refundNotice(appointment.examFee)}`
              : ""}
          </AlertDescription>
        </Alert>
      ) : null}
      <PatientAppointmentFacts appointment={appointment} />
      {queue.entry ? <PatientExamQueueStatus entry={queue.entry} /> : null}
      {queue.error ? (
        <p className="text-base text-destructive" role="alert">
          {messageFromError(queue.error)}
        </p>
      ) : null}
      {canArriveByQr ? (
        <PatientArrivalQrCheckIn
          appointment={appointment}
          onRecorded={() => void reload()}
        />
      ) : null}
      {appointment.status === "BOOKED" && started && paid ? (
        <p className="text-base text-muted-foreground">
          Lịch hẹn đã qua và phí khám đã thanh toán. Vui lòng liên hệ lễ tân để
          được hỗ trợ.
        </p>
      ) : null}
      {cancelWindowClosed ? (
        <p className="text-base text-muted-foreground">
          {formatAppMessage("MSG-32")}
        </p>
      ) : null}
      {appointment.status === "BOOKED" && !started && paid ? (
        <p className="text-base text-muted-foreground">
          {canSelfReschedule
            ? `Lịch hẹn đã thanh toán không thể tự hủy trực tuyến. Bạn có thể đổi lịch tối đa ${maxReschedules} lần trước giờ hẹn ít nhất 2 giờ (còn ${remainingReschedules}/${maxReschedules} lượt). Nếu cần hỗ trợ hoàn tiền hoặc thay đổi đặc biệt, vui lòng liên hệ quầy lễ tân.`
            : remainingReschedules <= 0
              ? `Bạn đã sử dụng hết ${maxReschedules}/${maxReschedules} lượt đổi lịch trực tuyến. Lịch hẹn đã thanh toán không thể tự hủy trực tuyến; nếu cần hỗ trợ đặc biệt, vui lòng liên hệ quầy lễ tân.`
              : "Đã quá hạn tự đổi lịch (ít nhất 2 giờ trước giờ hẹn). Lịch hẹn đã thanh toán không thể tự hủy trực tuyến; nếu cần hỗ trợ đặc biệt, vui lòng liên hệ quầy lễ tân."}
        </p>
      ) : null}
      {appointment.status === "BOOKED" && !started ? (
        <p className="text-base text-muted-foreground">
          {arrivalNote(patientCode)}
        </p>
      ) : null}
      <PatientAppointmentActions
        appointment={appointment}
        onRequestCancel={() => setCancelOpen(true)}
        onRequestReschedule={() => setRescheduleOpen(true)}
      />
      <PatientCancelDialog
        appointment={appointment}
        onCancelled={(next: PatientAppointment) => replace(next)}
        onOpenChange={setCancelOpen}
        onReload={reload}
        open={cancelOpen}
      />
      <RescheduleAppointmentDialog
        currentDoctorId={appointment.doctor.id}
        loadSlots={loadRescheduleSlots}
        onClose={() => setRescheduleOpen(false)}
        onConfirm={async (slot) => {
          const updated = await rescheduleMyAppointment(appointment.id, {
            doctorId: slot.doctorId,
            startTime: slot.startTime,
          });
          replace(updated);
        }}
        open={rescheduleOpen}
        requireReason={false}
      />
    </DetailFrame>
  );
}

/** Khung chung: đường về danh sách, tiêu đề (một h1) và nội dung. */
function DetailFrame({
  title,
  status,
  children,
}: {
  title: string;
  status?: PatientAppointment;
  children: ReactNode;
}) {
  return (
    <div className="space-y-8">
      <Link
        className="inline-flex min-h-11 items-center gap-1 text-base font-medium text-primary underline-offset-4 hover:underline"
        href={PATIENT_ROUTES.appointments}
      >
        <ChevronLeft aria-hidden className="size-5" />
        Lịch hẹn
      </Link>
      <PortalPageHeader
        description={
          status ? (
            <StatusBadge tone={APPOINTMENT_STATUS_TONE[status.status]}>
              {APPOINTMENT_STATUS_LABELS[status.status]}
            </StatusBadge>
          ) : undefined
        }
        title={title}
      />
      {children}
    </div>
  );
}

function DetailSkeleton() {
  return (
    <div aria-busy="true" className="space-y-4" role="status">
      <span className="sr-only">Đang tải lịch hẹn</span>
      <Skeleton className="h-6 w-24" />
      <div className="grid gap-5 rounded-lg border px-4 py-5 sm:grid-cols-2">
        {[0, 1, 2, 3, 4, 5].map((row) => (
          <div className="space-y-2" key={row}>
            <Skeleton className="h-4 w-20" />
            <Skeleton className="h-5 w-full" />
          </div>
        ))}
      </div>
    </div>
  );
}
