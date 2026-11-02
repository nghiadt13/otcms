import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { PatientBookingPage } from "@/features/appointments/components/portal/PatientBookingPage";

export const metadata = {
  title: "Đặt lịch khám | OTCMS",
};

// Đặt lịch khám theo wizard 4 bước. Luồng đặt nằm trong PatientBookingPage.
export default function NewPatientAppointmentPage() {
  return (
    <>
      <PortalPageHeader
        description="Chọn người khám, dịch vụ, ngày và giờ. Lịch vừa đặt hiện trong Lịch hẹn."
        title="Đặt lịch khám"
      />
      <PatientBookingPage />
    </>
  );
}
