import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { readBookingPreference } from "@/lib/routes/bookingSlotLink";
import { NewBookingScreen } from "./NewBookingScreen";

export const metadata = {
  title: "Đặt lịch khám | OTCMS",
};

// Đặt lịch khám theo wizard 5 bước (bước cuối là thanh toán). Luồng đặt nằm trong PatientBookingPage.
// `?serviceId=&doctorId=&date=&time=` (từ link giờ trống của trang bác sĩ) chọn sẵn khung giờ nếu còn trống.
export default async function NewPatientAppointmentPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const preselect = readBookingPreference(await searchParams);
  return (
    <>
      <PortalPageHeader title="Đặt lịch khám" />
      <NewBookingScreen preselect={preselect} />
    </>
  );
}
