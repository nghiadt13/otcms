import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";
import { Toaster } from "@/components/ui/sonner";
import { AdminHeader } from "@/features/admin-shell/components/AdminHeader";
import { AdminSidebar } from "@/features/admin-shell/components/AdminSidebar";

// Giai đoạn dựng giao diện: chưa kiểm tra đăng nhập/vai trò ở đây.
// Quyền thật phải do backend kiểm tra cho từng thao tác (xem AGENTS.md).
export default function AdminLayout({ children }: LayoutProps<"/admin">) {
  return (
    <SidebarProvider>
      <AdminSidebar />
      <SidebarInset>
        <AdminHeader />
        <div className="flex-1 p-4 md:p-6">{children}</div>
      </SidebarInset>
      <Toaster richColors={false} position="bottom-right" />
    </SidebarProvider>
  );
}
