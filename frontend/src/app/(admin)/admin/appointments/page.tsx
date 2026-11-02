import { Suspense } from "react";
import { AppointmentsManagement } from "@/features/appointments/components/AppointmentsManagement";

export const metadata = {
  title: "Lịch hẹn",
};

// useSearchParams trong feature cần Suspense để trang vẫn dựng tĩnh được.
export default function AdminAppointmentsPage() {
  return (
    <Suspense fallback={null}>
      <AppointmentsManagement />
    </Suspense>
  );
}
