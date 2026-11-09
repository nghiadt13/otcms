"use client";

import { PatientBookingPage } from "@/features/appointments/components/portal/PatientBookingPage";
import { AppointmentPayOsPayment } from "@/features/billing/components/portal/AppointmentPayOsPayment";

// Ghép wizard đặt lịch (feature appointments) với màn thanh toán PayOS (feature billing) ở tầng app,
// vì hai feature không được import nhau. Bước 5 nhận lịch vừa tạo (đang giữ giờ AWAITING_PAYMENT) và
// gọi BE hóa đơn/PayOS có sẵn. Hạn giữ giờ, hủy giữ chỗ và đặt lại do wizard cấp qua các prop dưới.
export function NewBookingScreen() {
  return (
    <PatientBookingPage
      renderPayment={({
        appointment,
        paymentDeadline,
        onFinish,
        onCancelHold,
        onBookAgain,
      }) => (
        <AppointmentPayOsPayment
          appointmentId={appointment.id}
          onBookAgain={onBookAgain}
          onCancelHold={onCancelHold}
          onFinish={onFinish}
          paymentDeadline={paymentDeadline}
        />
      )}
    />
  );
}
