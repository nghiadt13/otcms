"use client";

import { useEffect, useState } from "react";
import { StatusBadge, type StatusTone } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { apiRequest } from "@/lib/api/client";
import { formatDateTime } from "@/lib/format/formatDate";
import { messageFromError } from "@/lib/messages/appMessages";
import { cn } from "@/lib/utils";

type AppointmentDetail = {
  id: string;
  patientName: string | null;
  patientCode: string | null;
  serviceName: string | null;
  servicePrice: number | null;
  doctorDisplayName: string | null;
  status: string;
  bookingSource: string | null;
  purpose: string | null;
  startTime: string;
  endTime: string;
  durationMinutes: number;
  createdAt: string;
  examFeePaid: boolean | null;
  patientRescheduleCount?: number | null;
  remainingReschedules?: number | null;
  maxPatientReschedules?: number | null;
};

const STATUS_VIEW: Record<string, { label: string; tone: StatusTone }> = {
  CONFIRMED: { label: "Đã đặt", tone: "info" },
  BOOKED: { label: "Đã đặt", tone: "info" },
  CHECKED_IN: { label: "Đã tiếp nhận", tone: "success" },
  IN_PROGRESS: { label: "Đang khám", tone: "success" },
  COMPLETED: { label: "Đã hoàn thành", tone: "success" },
  CANCELLED: { label: "Đã hủy", tone: "neutral" },
  NO_SHOW: { label: "Không đến", tone: "warning" },
};

const BOOKING_SOURCE_LABELS: Record<string, string> = {
  PATIENT_ONLINE: "Bệnh nhân đặt trực tuyến",
  RECEPTIONIST_ASSISTED: "Lễ tân đặt giúp",
  DOCTOR_FOLLOW_UP: "Bác sĩ hẹn tái khám",
};

const currency = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
  maximumFractionDigits: 0,
});

function doctorName(value: string | null): string {
  if (!value) return "Chưa phân công";
  return /^BS\.?\s*/i.test(value) ? value : `BS. ${value}`;
}

export function DoctorAppointmentDetailSheet({
  appointmentId,
  onClose,
}: {
  appointmentId: string | null;
  onClose: () => void;
}) {
  const [loadedDetail, setLoadedDetail] = useState<{
    key: string;
    appointment: AppointmentDetail | null;
    error: string;
  } | null>(null);
  const [reload, setReload] = useState(0);
  const requestKey = appointmentId ? `${appointmentId}:${reload}` : "";
  const currentDetail = loadedDetail?.key === requestKey ? loadedDetail : null;
  const appointment = currentDetail?.appointment ?? null;
  const error = currentDetail?.error ?? "";
  const loading = appointmentId !== null && currentDetail === null;

  useEffect(() => {
    if (!appointmentId) return;

    const controller = new AbortController();
    const key = `${appointmentId}:${reload}`;
    apiRequest<AppointmentDetail>(
      `/api/v1/appointments/${encodeURIComponent(appointmentId)}`,
      { signal: controller.signal },
    )
      .then((data) =>
        setLoadedDetail({ key, appointment: data, error: "" }),
      )
      .catch((cause: unknown) => {
        if (!controller.signal.aborted)
          setLoadedDetail({
            key,
            appointment: null,
            error: messageFromError(cause),
          });
      });

    return () => controller.abort();
  }, [appointmentId, reload]);

  const status = appointment
    ? (STATUS_VIEW[appointment.status] ?? {
        label: "Chưa xác định",
        tone: "neutral" as const,
      })
    : null;

  return (
    <Sheet
      open={appointmentId !== null}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <SheetContent className="data-[side=right]:w-full data-[side=right]:overflow-hidden data-[side=right]:sm:max-w-2xl">
        <SheetHeader className="border-b pr-14">
          <SheetTitle>Chi tiết lịch hẹn</SheetTitle>
          <SheetDescription>
            Thông tin bệnh nhân, thời gian khám và tình trạng lịch.
          </SheetDescription>
        </SheetHeader>

        {loading ? (
          <div
            aria-label="Đang tải chi tiết lịch hẹn"
            className="space-y-4 px-4"
          >
            <Skeleton className="h-6 w-48" />
            <Skeleton className="h-28 w-full" />
            <Skeleton className="h-36 w-full" />
          </div>
        ) : error ? (
          <div role="alert" className="space-y-3 px-4">
            <p className="text-sm text-destructive">{error}</p>
            <Button
              variant="outline"
              onClick={() => setReload((value) => value + 1)}
            >
              Thử tải lại
            </Button>
          </div>
        ) : appointment && status ? (
          <div className="min-h-0 flex-1 space-y-3 overflow-y-auto px-4 pb-4">
            <section
              aria-labelledby="appointment-detail-patient"
              className="space-y-2"
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h2
                    id="appointment-detail-patient"
                    className="text-lg font-semibold"
                  >
                    {appointment.patientName ?? "Chưa có tên bệnh nhân"}
                  </h2>
                  {appointment.patientCode ? (
                    <p className="mt-1 text-sm text-muted-foreground">
                      Mã bệnh nhân: {appointment.patientCode}
                    </p>
                  ) : null}
                </div>
                <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
              </div>
              <dl className="grid gap-2 sm:grid-cols-2">
                <DetailCell
                  label="Thời gian khám"
                  value={formatDateTime(appointment.startTime)}
                />
                <DetailCell
                  label="Dự kiến kết thúc"
                  value={formatDateTime(appointment.endTime)}
                />
                <DetailCell
                  label="Thời lượng"
                  value={`${appointment.durationMinutes} phút`}
                />
                <DetailCell
                  label="Ngày tạo lịch"
                  value={formatDateTime(appointment.createdAt)}
                />
              </dl>
            </section>

            <section
              aria-labelledby="appointment-detail-service"
              className="space-y-2"
            >
              <h2
                id="appointment-detail-service"
                className="text-sm font-semibold"
              >
                Thông tin khám
              </h2>
              <dl className="grid gap-2 sm:grid-cols-2">
                <DetailCell
                  className="sm:col-span-2"
                  label="Dịch vụ"
                  value={appointment.serviceName ?? "Chưa xác định"}
                />
                <DetailCell
                  label="Bác sĩ"
                  value={doctorName(appointment.doctorDisplayName)}
                />
                {appointment.purpose ? (
                  <DetailCell
                    className="sm:col-span-2"
                    label="Lý do đặt lịch"
                    value={appointment.purpose}
                  />
                ) : null}
                <DetailCell
                  label="Nguồn đặt lịch"
                  value={
                    appointment.bookingSource
                      ? (BOOKING_SOURCE_LABELS[appointment.bookingSource] ??
                        "Khác")
                      : "Chưa có thông tin"
                  }
                />
              </dl>
            </section>

            <section
              aria-labelledby="appointment-detail-payment"
              className="space-y-2"
            >
              <h2
                id="appointment-detail-payment"
                className="text-sm font-semibold"
              >
                Phí khám
              </h2>
              <dl className="grid gap-2 sm:grid-cols-2">
                <DetailCell
                  label="Mức phí dịch vụ"
                  value={
                    appointment.servicePrice == null
                      ? "Chưa có thông tin"
                      : currency.format(appointment.servicePrice)
                  }
                />
                <DetailCell
                  label="Tình trạng thanh toán"
                  value={
                    appointment.examFeePaid === true
                      ? "Đã thanh toán"
                      : appointment.examFeePaid === false
                        ? "Chưa thanh toán"
                        : "Chưa cập nhật"
                  }
                />
              </dl>
            </section>

            {appointment.maxPatientReschedules != null ? (
              <p className="text-sm text-muted-foreground">
                Đã đổi lịch {appointment.patientRescheduleCount ?? 0} /{" "}
                {appointment.maxPatientReschedules} lần
                {appointment.remainingReschedules != null
                  ? ` · Còn ${appointment.remainingReschedules} lần`
                  : ""}
              </p>
            ) : null}
          </div>
        ) : null}
      </SheetContent>
    </Sheet>
  );
}

function DetailCell({
  label,
  value,
  className,
}: {
  label: string;
  value: string;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "grid content-start gap-1 rounded-lg border px-3 py-2.5",
        className,
      )}
    >
      <dt className="text-xs font-medium text-muted-foreground">{label}</dt>
      <dd className="whitespace-pre-wrap text-sm font-medium leading-5">
        {value}
      </dd>
    </div>
  );
}
