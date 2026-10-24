import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";
import { Toaster } from "@/components/ui/sonner";
import { StaffHeader } from "@/features/staff-shell/components/StaffHeader";
import { StaffSidebar } from "@/features/staff-shell/components/StaffSidebar";

// Khung khu Lễ tân: sidebar công cụ + header. Chưa kiểm tra đăng nhập/vai trò ở đây;
// quyền thật do backend kiểm cho từng thao tác (xem AGENTS.md).
export default function StaffLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <SidebarProvider>
      <StaffSidebar />
      <SidebarInset>
        <StaffHeader />
        <main className="flex-1 p-4 md:p-6">{children}</main>
      </SidebarInset>
      <Toaster richColors={false} position="bottom-right" />
    </SidebarProvider>
  );
}
