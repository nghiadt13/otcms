import type { Metadata } from "next";
import { Suspense } from "react";
import { StaffAppointmentList } from "@/features/appointments/components/staff/StaffAppointmentList";

export const metadata: Metadata = {
  title: "Lịch hẹn",
};

// useSearchParams trong feature cần Suspense để trang vẫn dựng tĩnh được.
export default function StaffAppointmentsPage() {
  return (
    <Suspense fallback={null}>
      <StaffAppointmentList />
    </Suspense>
  );
}
