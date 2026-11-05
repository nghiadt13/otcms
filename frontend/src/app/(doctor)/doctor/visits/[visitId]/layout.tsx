import { DoctorVisitPatientHeader } from "@/features/doctor-shell/components/DoctorVisitPatientHeader";

// Khung workspace khám: thanh thông tin tối thiểu của bệnh nhân luôn hiện ở mọi màn của lượt khám.
export default async function DoctorVisitLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ visitId: string }>;
}) {
  const { visitId } = await params;
  return (
    <>
      <DoctorVisitPatientHeader visitId={visitId} />
      {children}
    </>
  );
}
