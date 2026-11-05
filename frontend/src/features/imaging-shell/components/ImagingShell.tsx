"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { getSession, signOut, type AuthSession } from "@/lib/auth/authSession";
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
  const { toast, reset, ready } = useImaging();
  const [session, setSession] = useState<AuthSession | null>(null);
  const [checking, setChecking] = useState(true);
  const [resetOpen, setResetOpen] = useState(false);
  const [exitOpen, setExitOpen] = useState(false);

  useEffect(() => {
    if (pathname === "/imaging/login") return;
    let active = true;
    getSession()
      .then((value) => {
        if (!active) return;
        if (!value) router.replace("/imaging/login");
        else if (value.accountType !== "IMAGING_TECHNICIAN")
          router.replace("/workspace");
        else setSession(value);
      })
      .catch(() => {
        if (active) router.replace("/imaging/login");
      })
      .finally(() => {
        if (active) setChecking(false);
      });
    return () => {
      active = false;
    };
  }, [pathname, router]);

  if (pathname === "/imaging/login")
    return <div className="imaging">{children}</div>;
  if (checking || !session)
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

  // The real upload worklist and operation screens do not consume demo imaging state.
  if (pathname === "/imaging/orders" || pathname.startsWith("/imaging/orders/"))
    return <div className="imaging min-h-dvh">{children}</div>;

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
  return (
    <div className="imaging">
      <a className="im-skip" href="#im-content">
        Chuyển đến nội dung
      </a>
      <SidebarProvider>
        <ImagingSidebar
          onReset={() => setResetOpen(true)}
          onExit={() => setExitOpen(true)}
        />
        <SidebarInset className="min-w-0">
          <ImagingHeader
            onReset={() => setResetOpen(true)}
            onExit={() => setExitOpen(true)}
          />
          <div className="flex flex-wrap items-center justify-between gap-2 border-b bg-secondary/50 px-4 py-2 text-xs text-muted-foreground md:px-6">
            <span>
              {pathname === "/imaging/rooms"
                ? "Danh sách phòng chẩn đoán hình ảnh · dữ liệu từ hệ thống"
                : pathname === "/imaging/change-password"
                  ? "Đổi mật khẩu · kết nối hệ thống"
                  : "Bản xem thử giao diện · dữ liệu mẫu · giờ mô phỏng 09:10"}
            </span>
            {pathname !== "/imaging/rooms" &&
              pathname !== "/imaging/change-password" && (
                <span>{ROOM_NAME} · Ca sáng 07:00 – 12:00</span>
              )}
          </div>
          <main
            id="im-content"
            className="im-content min-w-0 flex-1 p-4 text-sm md:p-6"
          >
            {session.mustChangePassword &&
            pathname !== "/imaging/change-password" ? (
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
        title="Đăng xuất?"
        description="Bạn sẽ quay về màn đăng nhập nhân viên."
        confirmLabel="Đăng xuất"
        pendingLabel="Đang đăng xuất..."
        onConfirm={async () => {
          await signOut();
          router.replace("/imaging/login");
        }}
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
