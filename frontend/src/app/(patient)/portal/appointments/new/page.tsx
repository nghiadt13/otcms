import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { PatientBookingPage } from "@/features/appointments/components/portal/PatientBookingPage";

export const metadata = {
  title: "Đặt lịch khám | OTCMS",
};

// Đặt lịch cho bản thân hoặc người thân đã gắn. Luồng đặt nằm trong PatientBookingPage.
// `?profileId=` chọn sẵn người khám (ví dụ từ trang hồ sơ); mã không thuộc hồ sơ đã gắn bị bỏ qua.
export default async function NewPatientAppointmentPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  const initialPatientId =
    typeof params.profileId === "string" ? params.profileId : undefined;
  return (
    <>
      <PortalPageHeader
        description="Chọn người khám, dịch vụ, ngày và giờ. Lịch vừa đặt hiện trong Lịch hẹn."
        title="Đặt lịch khám"
      />
      <PatientBookingPage initialPatientId={initialPatientId} />
    </>
  );
}
