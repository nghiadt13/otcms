"use client";

import { PatientBookingPage } from "@/features/appointments/components/portal/PatientBookingPage";
import { AppointmentPayOsPayment } from "@/features/billing/components/portal/AppointmentPayOsPayment";

// Ghép wizard đặt lịch (feature appointments) với màn thanh toán PayOS (feature billing) ở tầng app,
// vì hai feature không được import nhau. Bước 5 nhận lịch vừa tạo và gọi BE hóa đơn/PayOS có sẵn.
export function NewBookingScreen() {
  return (
    <PatientBookingPage
      renderPayment={({ appointment, onFinish }) => (
        <AppointmentPayOsPayment
          appointmentId={appointment.id}
          onFinish={onFinish}
        />
      )}
    />
  );
}
