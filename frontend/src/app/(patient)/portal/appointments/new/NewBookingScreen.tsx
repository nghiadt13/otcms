"use client";

import { PatientBookingPage } from "@/features/appointments/components/portal/PatientBookingPage";
import { AppointmentPayOsPayment } from "@/features/billing/components/portal/AppointmentPayOsPayment";
import type { BookingSlotQuery } from "@/lib/routes/bookingSlotLink";

// Ghép wizard đặt lịch (feature appointments) với màn thanh toán PayOS (feature billing) ở tầng app,
// vì hai feature không được import nhau. Bước 5 (tùy chọn, trả ngay hoặc trả sau) nhận lịch vừa tạo
// và gọi BE hóa đơn/PayOS có sẵn. Không giữ giờ nên không còn hạn thanh toán hay hủy giữ chỗ.
// `preselect` là khung giờ từ link trang bác sĩ (đã đọc ở page.tsx).
export function NewBookingScreen({
  preselect = null,
}: {
  preselect?: BookingSlotQuery | null;
}) {
  return (
    <PatientBookingPage
      preselect={preselect}
      renderPayment={({ appointment, onFinish }) => (
        <AppointmentPayOsPayment
          appointmentId={appointment.id}
          onFinish={onFinish}
        />
      )}
    />
  );
}
