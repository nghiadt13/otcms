import { VisitPatientHeader } from "@/features/doctor-exam/components/VisitPatientHeader";
import { VisitWorkspaceProvider } from "@/features/doctor-exam/components/VisitWorkspaceProvider";

// Khung workspace khám: thông tin tối thiểu của bệnh nhân (BR-13) luôn hiện ở mọi tab của lượt khám.
export default async function DoctorVisitLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ visitId: string }>;
}) {
  const { visitId } = await params;
  return (
    <VisitWorkspaceProvider visitId={visitId}>
      <VisitPatientHeader />
      {children}
    </VisitWorkspaceProvider>
  );
}
