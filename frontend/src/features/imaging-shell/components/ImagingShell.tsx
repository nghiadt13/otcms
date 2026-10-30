"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState, type ReactNode } from "react";
import { buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";
import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import { useImaging } from "@/features/imaging/hooks/useImaging";
import { ImagingProvider } from "@/features/imaging/components/ImagingProvider";
import { ROOM_NAME } from "@/features/imaging/api/imagingMockData";
import { Icon } from "@/features/imaging/components/Icon";
import "@/features/imaging/styles/imaging.css";
import { ImagingHeader } from "./ImagingHeader";
import { ImagingSidebar } from "./ImagingSidebar";

function ShellContent({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { toast, reset, mustChangePassword, ready } = useImaging();
  const [resetOpen, setResetOpen] = useState(false);
  const [exitOpen, setExitOpen] = useState(false);

  if (!ready)
    return (
      <div
        className="space-y-4 p-6"
        role="status"
        aria-label="Đang mở không gian làm việc"
      >
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  if (pathname === "/imaging/login")
    return <div className="imaging">{children}</div>;
  return (
    <div className="imaging">
      <a className="im-skip" href="#im-content">
        Chuyển đến nội dung
      </a>
      <SidebarProvider>
        <ImagingSidebar />
        <SidebarInset className="min-w-0">
          <ImagingHeader
            onReset={() => setResetOpen(true)}
            onExit={() => setExitOpen(true)}
          />
          <div className="flex flex-wrap items-center justify-between gap-2 border-b bg-secondary/50 px-4 py-2 text-xs text-muted-foreground md:px-6">
            <span>
              Bản xem thử giao diện · dữ liệu mẫu · giờ mô phỏng 09:10
            </span>
            <span>{ROOM_NAME} · Ca sáng 07:00 – 12:00</span>
          </div>
          <main
            id="im-content"
            className="im-content min-w-0 flex-1 p-4 text-sm md:p-6"
          >
            {mustChangePassword && pathname !== "/imaging/change-password" ? (
              <div className="space-y-4 rounded-lg border bg-card p-6">
                <h1 className="text-2xl font-semibold tracking-tight">
                  Cần đổi mật khẩu được cấp
                </h1>
                <p>
                  Hoàn tất đổi mật khẩu trước khi sử dụng không gian làm việc.
                </p>
                <Link
                  className={buttonVariants()}
                  href="/imaging/change-password"
                >
                  Đổi mật khẩu
                </Link>
              </div>
            ) : (
              children
            )}
          </main>
        </SidebarInset>
      </SidebarProvider>
      <ConfirmActionDialog
        open={resetOpen}
        onOpenChange={setResetOpen}
        title="Khôi phục dữ liệu mẫu?"
        description="Các thao tác trong bản xem thử sẽ được đặt lại."
        confirmLabel="Khôi phục"
        pendingLabel="Đang khôi phục..."
        onConfirm={() => {
          reset();
          setResetOpen(false);
        }}
      />
      <ConfirmActionDialog
        open={exitOpen}
        onOpenChange={setExitOpen}
        title="Đăng xuất bản xem thử?"
        description="Bạn sẽ quay về màn đăng nhập nhân viên."
        confirmLabel="Đăng xuất"
        pendingLabel="Đang đăng xuất..."
        onConfirm={() => router.push("/imaging/login")}
      />
      {toast && (
        <div className="im-toast" role="status" aria-live="polite">
          <Icon name="check" />
          <span>{toast}</span>
        </div>
      )}
    </div>
  );
}
export function ImagingShell({ children }: { children: ReactNode }) {
  return (
    <ImagingProvider>
      <ShellContent>{children}</ShellContent>
    </ImagingProvider>
  );
}
