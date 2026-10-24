"use client";

import Link from "next/link";
import { useEffect, useId, useRef, useState } from "react";
import { Button, buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { isApiError } from "@/lib/api/apiError";
import { formatDate, todayIsoDate } from "@/lib/format/formatDate";
import { notifyError } from "@/lib/messages/notify";
import { STAFF_ROUTES } from "@/lib/routes/staffRoutes";
import { useBookingPatient } from "../../hooks/useBookingPatient";
import { dateOfStart } from "../../schema/bookingTime";
import { checkInHref } from "../../schema/staffAppointmentUrlRules";
import type { BookedAppointment } from "../../types/booking";
import { BookingResult } from "../BookingResult";
import { BookingFlow } from "../booking/BookingFlow";

type StaffBookingBodyProps = {
  patientId: string;
  /** Xóa mã bệnh nhân khỏi URL và đưa con trỏ về ô tìm. */
  onChangePatient: () => void;
};

/** Mã không còn dùng được (404 hoặc 403 MSG-29): toast rồi xóa mã khỏi URL. */
function isMissingPatientError(error: unknown): boolean {
  return isApiError(error) && (error.status === 404 || error.status === 403);
}

/** Khối đặt lịch cho một bệnh nhân đã tìm được. Panel dựng lại khối này khi đổi người. */
export function StaffBookingBody({ patientId, onChangePatient }: StaffBookingBodyProps) {
  const headingId = useId();
  const { patient, error, loading, reload } = useBookingPatient(patientId);
  const [booked, setBooked] = useState<BookedAppointment | null>(null);
  const handledError = useRef<unknown>(null);

  // Mã sai hoặc người bị khóa: báo một lần rồi bỏ mã khỏi URL để lễ tân tìm lại.
  useEffect(() => {
    if (!error || handledError.current === error || !isMissingPatientError(error))
      return;
    handledError.current = error;
    notifyError(error);
    onChangePatient();
  }, [error, onChangePatient]);

  if (loading) return <BookingBodySkeleton />;
  if (error)
    return isMissingPatientError(error) ? null : (
      <BookingLoadFailure onRetry={reload} />
    );
  if (!patient) return null;

  if (booked)
    return <BookedResult appointment={booked} onStartOver={onChangePatient} />;

  return (
    <section aria-labelledby={headingId} className="grid gap-4 rounded-lg border bg-card p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="grid gap-1">
          <h2 id={headingId} className="text-lg font-semibold">
            Đặt lịch khám
          </h2>
          <p className="text-sm">
            Người khám: <span className="font-medium">{patient.fullName}</span>
            {" · "}ngày sinh{" "}
            <span className="tabular-nums">{formatDate(patient.dateOfBirth)}</span>
          </p>
        </div>
        <Button variant="link" onClick={onChangePatient}>
          Đổi người
        </Button>
      </div>
      <BookingFlow
        key={patient.id}
        variant="staff"
        patient={patient}
        onBooked={setBooked}
      />
    </section>
  );
}

/** Kết quả sau khi đặt. Lịch hôm nay có lối tắt sang tiếp nhận ngay; "Đặt lịch khác" quay về tìm người. */
function BookedResult({
  appointment,
  onStartOver,
}: {
  appointment: BookedAppointment;
  onStartOver: () => void;
}) {
  const isToday = dateOfStart(appointment.startAt) === todayIsoDate();
  return (
    <div className="rounded-lg border bg-card p-4">
      <BookingResult
        appointment={appointment}
        variant="staff"
        actions={
          <>
            {isToday ? (
              <Link
                href={checkInHref(appointment.id)}
                className={buttonVariants({ variant: "default" })}
              >
                Tiếp nhận ngay
              </Link>
            ) : null}
            <Button variant="outline" onClick={onStartOver}>
              Đặt lịch khác
            </Button>
            <Link
              href={STAFF_ROUTES.appointments}
              className={buttonVariants({ variant: "link" })}
            >
              Về danh sách
            </Link>
          </>
        }
      />
    </div>
  );
}

function BookingBodySkeleton() {
  return (
    <div className="grid gap-3 rounded-lg border bg-card p-4" aria-hidden>
      <Skeleton className="h-6 w-48" />
      <Skeleton className="h-4 w-72 max-w-full" />
      <Skeleton className="h-9 w-full" />
    </div>
  );
}

function BookingLoadFailure({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="grid gap-3 rounded-lg border bg-card p-4">
      <p role="alert" className="text-sm text-destructive">
        Chưa tải được thông tin người khám. Vui lòng thử lại.
      </p>
      <div>
        <Button variant="outline" size="sm" onClick={onRetry}>
          Thử lại
        </Button>
      </div>
    </div>
  );
}
