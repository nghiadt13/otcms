import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";
import { Toaster } from "@/components/ui/sonner";
import { AdminHeader } from "@/features/admin-shell/components/AdminHeader";
import { AdminSidebar } from "@/features/admin-shell/components/AdminSidebar";
import { AdminAccess } from "@/features/admin-shell/components/AdminAccess";

// Gate the entire Admin shell; Spring still authorizes every data request.
export default function AdminLayout({ children }: LayoutProps<"/admin">) {
  return (
    <AdminAccess>
      <SidebarProvider>
        <AdminSidebar />
        <SidebarInset>
          <AdminHeader />
          <div className="flex-1 p-4 md:p-6">{children}</div>
        </SidebarInset>
        <Toaster richColors={false} position="bottom-right" />
      </SidebarProvider>
    </AdminAccess>
  );
}
