"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Bell, Bone, LogOut, RefreshCw } from "lucide-react";
import { useState, type ReactNode } from "react";
import { Button, buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarRail,
  SidebarTrigger,
  useSidebar,
} from "@/components/ui/sidebar";
import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import { ImagingProvider, useImaging } from "../ImagingProvider";
import { ROOM_NAME } from "../demoData";
import { Icon } from "./ImagingUi";
import "../imaging.css";

const sections = [
  {
    title: "Công việc",
    links: [
      ["/imaging", "Tổng quan", "grid"],
      ["/imaging/worklist", "Danh sách chờ", "queue"],
      ["/imaging/orders", "Chỉ định hình ảnh", "file"],
      ["/imaging/status", "Trạng thái chỉ định", "activity"],
      ["/imaging/visits", "Tiến trình lượt khám", "layers"],
    ],
  },
  {
    title: "Thông tin công việc",
    links: [
      ["/imaging/rooms", "Phòng dịch vụ", "room"],
      ["/imaging/schedule", "Lịch làm việc", "calendar"],
      ["/imaging/notifications", "Thông báo", "bell"],
    ],
  },
  {
    title: "Cá nhân",
    links: [
      ["/imaging/profile", "Tài khoản của tôi", "user"],
      ["/imaging/change-password", "Đổi mật khẩu", "lock"],
    ],
  },
];

function Menu() {
  const pathname = usePathname();
  const { setOpenMobile } = useSidebar();
  const { state } = useImaging();
  const active = (href: string) =>
    href === "/imaging" ? pathname === href : pathname.startsWith(href);
  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              size="lg"
              render={<Link href="/imaging" />}
              tooltip="OTCMS · Chẩn đoán hình ảnh"
            >
              <span className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                <Bone className="size-4" aria-hidden />
              </span>
              <span className="grid text-left">
                <span className="font-semibold">OTCMS</span>
                <span className="text-xs text-muted-foreground">
                  Khu vực kỹ thuật viên
                </span>
              </span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        {sections.map((section) => (
          <SidebarGroup key={section.title}>
            <SidebarGroupLabel>{section.title}</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {section.links.map(([href, label, icon]) => (
                  <SidebarMenuItem key={href}>
                    <SidebarMenuButton
                      render={<Link href={href} />}
                      tooltip={label}
                      isActive={active(href)}
                      onClick={() => setOpenMobile(false)}
                    >
                      <Icon name={icon} />
                      <span>{label}</span>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        ))}
      </SidebarContent>
      <SidebarFooter>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              size="lg"
              render={<Link href="/imaging/profile" />}
              tooltip={state.profile.name}
            >
              <span className="flex size-7 shrink-0 items-center justify-center overflow-hidden rounded-full bg-secondary text-xs font-semibold text-secondary-foreground">
                {state.profile.avatar ? (
                  <Image
                    src={state.profile.avatar}
                    alt="Ảnh đại diện"
                    width={28}
                    height={28}
                    unoptimized
                  />
                ) : (
                  "MA"
                )}
              </span>
              <span className="grid text-left">
                <span className="font-medium">{state.profile.name}</span>
                <span className="text-xs text-muted-foreground">
                  Kỹ thuật viên CĐHA
                </span>
              </span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  );
}

function ShellContent({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { state, toast, reset, mustChangePassword, ready } = useImaging();
  const [resetOpen, setResetOpen] = useState(false);
  const [exitOpen, setExitOpen] = useState(false);
  const unread = state.notifications.filter((n) => !n.read).length;
  const currentItem = sections
    .flatMap((s) => s.links)
    .filter(([href]) =>
      href === "/imaging" ? pathname === href : pathname.startsWith(href),
    )
    .at(-1);
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
        <Menu />
        <SidebarInset className="min-w-0">
          <header className="flex h-14 shrink-0 items-center gap-3 border-b px-4">
            <SidebarTrigger aria-label="Mở hoặc thu gọn menu" />
            <Link
              href={currentItem?.[0] ?? "/imaging"}
              className="truncate text-sm font-medium"
              aria-label={`Quay lại ${(currentItem?.[1] ?? "Tổng quan").toLowerCase()}`}
            >
              {currentItem?.[1] ?? "Chẩn đoán hình ảnh"}
            </Link>
            <div className="ml-auto flex items-center gap-2">
              <span className="hidden text-xs text-muted-foreground md:block">
                24/10/2026 · 09:10
              </span>
              <Button
                variant="ghost"
                size="icon"
                aria-label="Khôi phục dữ liệu mẫu"
                onClick={() => setResetOpen(true)}
              >
                <RefreshCw />
              </Button>
              <Link
                href="/imaging/notifications"
                className={buttonVariants({ variant: "ghost", size: "icon" })}
                aria-label={`Thông báo, ${unread} chưa đọc`}
              >
                <Bell />
              </Link>
              <Button
                variant="ghost"
                size="icon"
                aria-label="Đăng xuất"
                onClick={() => setExitOpen(true)}
              >
                <LogOut />
              </Button>
            </div>
          </header>
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
