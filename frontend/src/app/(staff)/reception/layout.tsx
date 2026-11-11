import { Toaster } from "@/components/ui/sonner";
import { ReceptionShell } from "@/features/patient-reception/components/ReceptionShell";

// Cổng production theo từng màn nằm ở ReceptionScreen (isReceptionSectionAvailable): màn đã nối BE
// mở được không cần NEXT_PUBLIC_RECEPTION_DEMO. Toaster nằm ở đây vì khu lễ tân chưa có Toaster riêng.
export default function ReceptionLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <>
      <ReceptionShell>{children}</ReceptionShell>
      <Toaster richColors={false} position="bottom-right" />
    </>
  );
}
