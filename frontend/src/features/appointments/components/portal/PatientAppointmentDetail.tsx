"use client";

import Link from "next/link";
import { notFound } from "next/navigation";
import { useState, type ReactNode } from "react";
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
import { messageFromError } from "@/lib/messages/appMessages";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { usePatientAppointment } from "../../hooks/usePatientAppointment";
import type { PatientAppointment } from "../../types/patientAppointment";
import { PatientAppointmentActions } from "./PatientAppointmentActions";
import { PatientAppointmentFacts } from "./PatientAppointmentFacts";
import { PatientCancelDialog } from "./PatientCancelDialog";
import { PatientLoadError } from "./PatientLoadError";
import {
  appointmentDetailTitle,
  arrivalNote,
  refundNotice,
} from "./patientAppointmentPresentation";

// Chi tiết một lịch hẹn (UC23, UC24). Lịch không có hoặc ngoài phạm vi (404/403 MSG-29) đi tới
// trang không tìm thấy của khu, không lộ lý do. Mọi nút theo `allowedActions` của BE.

export function PatientAppointmentDetail({ id }: { id: string }) {
  const { appointment, loading, error, reload, replace } =
    usePatientAppointment(id);
  const { profiles } = useLinkedProfiles();
  const [cancelOpen, setCancelOpen] = useState(false);

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

  return (
    <DetailFrame title={appointmentDetailTitle(appointment.startAt)} status={appointment}>
      {appointment.doctorUnavailable && appointment.status === "BOOKED" ? (
        <Alert variant="destructive">
          <AlertDescription className="text-base">
            Bác sĩ của lịch hẹn này không thể khám và chưa có bác sĩ thay thế. Bạn có thể hủy lịch hẹn.
            {appointment.examFee.status === "PAID"
              ? ` ${refundNotice(appointment.examFee)}`
              : ""}
          </AlertDescription>
        </Alert>
      ) : null}
      <PatientAppointmentFacts appointment={appointment} />
      {appointment.status === "BOOKED" ? (
        <p className="text-base text-muted-foreground">{arrivalNote(patientCode)}</p>
      ) : null}
      <PatientAppointmentActions
        appointment={appointment}
        onRequestCancel={() => setCancelOpen(true)}
      />
      <PatientCancelDialog
        appointment={appointment}
        onCancelled={(next: PatientAppointment) => replace(next)}
        onOpenChange={setCancelOpen}
        onReload={reload}
        open={cancelOpen}
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
