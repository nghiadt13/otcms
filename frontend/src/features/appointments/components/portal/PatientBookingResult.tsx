"use client";

import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import type { BookedAppointment } from "../../types/booking";
import { BookingResult } from "../BookingResult";

// Kết quả sau bước xác nhận đặt lịch. Lịch đã đặt và còn phí khám sẽ có đường dẫn tới hóa đơn PayOS.

export function PatientBookingResult({
  appointment,
}: {
  appointment: BookedAppointment;
}) {
  const canPay =
    appointment.status === "BOOKED" &&
    appointment.examFee.status === "PENDING_PAYMENT" &&
    appointment.examFee.amount > 0;

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
              Thanh toán phí khám
            </Link>
          ) : null}
        </>
      }
      variant="patient"
    />
  );
}
