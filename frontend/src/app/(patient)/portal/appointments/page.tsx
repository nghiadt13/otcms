import { Suspense } from "react";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { PatientAppointmentList } from "@/features/appointments/components/portal/PatientAppointmentList";
import { PatientAppointmentListSkeleton } from "@/features/appointments/components/portal/PatientAppointmentListBody";

export const metadata = {
  title: "Lịch hẹn | OTCMS",
};

// Danh sách lịch hẹn. Tab và người khám đọc từ URL nên danh sách nằm trong Suspense.
export default function PatientAppointmentsPage() {
  return (
    <>
      <PortalPageHeader
        description="Lịch khám sắp tới và đã qua của bạn và người thân."
        title="Lịch hẹn"
      />
      <Suspense fallback={<PatientAppointmentListSkeleton />}>
        <PatientAppointmentList />
      </Suspense>
    </>
  );
}
