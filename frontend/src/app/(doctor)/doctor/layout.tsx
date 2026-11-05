import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";
import { Toaster } from "@/components/ui/sonner";
import { DoctorHeader } from "@/features/doctor-shell/components/DoctorHeader";
import { DoctorSidebar } from "@/features/doctor-shell/components/DoctorSidebar";
import { DoctorDemoSwitch } from "@/features/doctor-exam/components/DoctorDemoSwitch";

// Giai đoạn dựng giao diện: chưa kiểm tra đăng nhập/vai trò ở đây.
// Quyền thật phải do backend kiểm tra cho từng thao tác (xem AGENTS.md).
export default function DoctorLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <SidebarProvider>
      <DoctorSidebar />
      <SidebarInset className="min-w-0">
        <DoctorHeader extra={<DoctorDemoSwitch />} />
        <div className="flex-1 p-4 md:p-6">{children}</div>
      </SidebarInset>
      <Toaster richColors={false} position="bottom-right" />
    </SidebarProvider>
  );
}
