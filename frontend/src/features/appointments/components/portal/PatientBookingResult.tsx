"use client";

import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import { usePatientAppointment } from "../../hooks/usePatientAppointment";
import type { BookedAppointment } from "../../types/booking";
import { BookingResult } from "../BookingResult";

// Kết quả sau khi đặt (khu bệnh nhân). "Thanh toán trực tuyến" chỉ hiện khi chi tiết lịch do BE trả
// có `payExamFee` trong `allowedActions`; hình trả của lúc đặt không có mảng đó nên FE tải chi tiết.

export function PatientBookingResult({
  appointment,
}: {
  appointment: BookedAppointment;
}) {
  const { appointment: detail } = usePatientAppointment(appointment.id);
  const canPay = detail?.allowedActions.includes("payExamFee") === true;

  return (
    <BookingResult
      appointment={appointment}
      actions={
        <>
          <Link
            className={cn(publicButtonClass("primary"), "w-full sm:w-auto")}
            href={PATIENT_ROUTES.appointmentDetail(appointment.id)}
          >
            Xem lịch hẹn
          </Link>
          {canPay ? (
            <Link
              className={cn(publicButtonClass("secondary"), "w-full sm:w-auto")}
              href={PATIENT_ROUTES.payAppointment(appointment.id)}
            >
              Thanh toán trực tuyến
            </Link>
          ) : null}
        </>
      }
      variant="patient"
    />
  );
}
