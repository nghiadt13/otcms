import { PatientAppointmentDetail } from "@/features/appointments/components/portal/PatientAppointmentDetail";

export const metadata = {
  title: "Chi tiết lịch hẹn | OTCMS",
};

// `key` theo mã lịch: chuyển sang lịch khác thì màn tải lại từ đầu, không giữ dữ liệu cũ.
export default async function PatientAppointmentDetailPage({
  params,
}: {
  params: Promise<{ appointmentId: string }>;
}) {
  const { appointmentId } = await params;
  return (
    <PatientAppointmentDetail key={appointmentId} id={appointmentId} />
  );
}
