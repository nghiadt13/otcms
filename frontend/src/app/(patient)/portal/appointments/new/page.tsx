import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { NewBookingScreen } from "./NewBookingScreen";

export const metadata = {
  title: "Đặt lịch khám | OTCMS",
};

// Đặt lịch khám theo wizard 5 bước (bước cuối là thanh toán). Luồng đặt nằm trong PatientBookingPage.
export default function NewPatientAppointmentPage() {
  return (
    <>
      <PortalPageHeader title="Đặt lịch khám" />
      <NewBookingScreen />
    </>
  );
}
