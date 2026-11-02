"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  Activity,
  Bell,
  Bone,
  CalendarDays,
  ChevronRight,
  FileText,
  Hospital,
  Layers,
  LayoutDashboard,
  ListOrdered,
} from "lucide-react";
import { useState, type ReactNode } from "react";
import { CurrentDateTime } from "@/components/shell/CurrentDateTime";
import {
  SidebarNavGroup,
  type SidebarNavGroupData,
} from "@/components/shell/SidebarNavGroup";
import { UnreadCountBadge } from "@/components/shell/UnreadCountBadge";
import { buttonVariants } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
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
import { cn } from "@/lib/utils";
import { ImagingProvider, useImaging } from "../ImagingProvider";
import { ROOM_NAME } from "../demoData";
import { Icon } from "./ImagingUi";
import { ImagingAccountMenu } from "./ImagingAccountMenu";
import "../imaging.css";

// Cùng dạng sidebar khu admin (SidebarNavGroup). "Tổng quan" là liên kết cấp 1.
const navGroups: SidebarNavGroupData[] = [
  {
    label: "Tổng quan",
    standalone: true,
    items: [{ title: "Tổng quan", href: "/imaging", icon: LayoutDashboard }],
  },
  {
    label: "Công việc",
    items: [
      { title: "Danh sách chờ", href: "/imaging/worklist", icon: ListOrdered },
      { title: "Chỉ định hình ảnh", href: "/imaging/orders", icon: FileText },
      { title: "Trạng thái chỉ định", href: "/imaging/status", icon: Activity },
      { title: "Tiến trình lượt khám", href: "/imaging/visits", icon: Layers },
    ],
  },
  {
    label: "Thông tin công việc",
    items: [
      { title: "Phòng dịch vụ", href: "/imaging/rooms", icon: Hospital },
      { title: "Lịch làm việc", href: "/imaging/schedule", icon: CalendarDays },
      { title: "Thông báo", href: "/imaging/notifications", icon: Bell },
    ],
  },
];

// Mục khớp tiền tố dài nhất là mục đang mở; "/imaging" chỉ khớp chính xác.
function findActive(pathname: string) {
  let found:
    { group: SidebarNavGroupData; title: string; href: string } | undefined;
  for (const group of navGroups)
    for (const item of group.items) {
      const hit =
        item.href === "/imaging"
          ? pathname === item.href
          : pathname.startsWith(item.href);
      if (hit && (!found || item.href.length > found.href.length))
        found = { group, title: item.title, href: item.href };
    }
  return found;
}

function Menu({
  activeHref,
  onReset,
  onExit,
}: {
  activeHref: string | undefined;
  onReset: () => void;
  onExit: () => void;
}) {
  const { setOpenMobile } = useSidebar();
  // Trạng thái do người dùng tự bấm; chưa bấm thì mọi nhóm mặc định mở (như admin).
  const [userOpen, setUserOpen] = useState<Record<string, boolean>>({});
  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              size="lg"
              render={<Link href="/imaging" />}
              tooltip="OTCMS Kỹ thuật viên"
            >
              <span className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                <Bone className="size-4" aria-hidden />
              </span>
              <span className="grid flex-1 text-left leading-tight">
                <span className="truncate font-semibold">OTCMS</span>
                <span className="truncate text-xs text-muted-foreground">
                  Khu vực kỹ thuật viên
                </span>
              </span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        {navGroups.map((group) => (
          <SidebarNavGroup
            key={group.label}
            group={group}
            activeHref={activeHref}
            open={userOpen[group.label] ?? true}
            onOpenChange={(open) =>
              setUserOpen((current) => ({ ...current, [group.label]: open }))
            }
            onNavigate={() => setOpenMobile(false)}
          />
        ))}
      </SidebarContent>
      <SidebarFooter>
        <ImagingAccountMenu onReset={onReset} onExit={onExit} />
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
  const active = findActive(pathname);
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
        <Menu
          activeHref={active?.href}
          onReset={() => setResetOpen(true)}
          onExit={() => setExitOpen(true)}
        />
        <SidebarInset className="min-w-0">
          <header className="flex h-14 shrink-0 items-center gap-2 border-b px-4">
            <SidebarTrigger
              className="-ml-1"
              aria-label="Mở hoặc thu gọn menu"
            />
            <Separator orientation="vertical" className="mr-2 h-4" />
            <nav aria-label="Breadcrumb" className="flex min-w-0 items-center">
              <ol className="flex min-w-0 items-center gap-1.5 text-xs sm:text-sm">
                {active && !active.group.standalone && (
                  <>
                    <li className="hidden items-center text-muted-foreground sm:inline-flex">
                      {active.group.label}
                    </li>
                    <li
                      className="hidden items-center text-muted-foreground sm:inline-flex"
                      aria-hidden="true"
                    >
                      <ChevronRight className="size-3.5" />
                    </li>
                  </>
                )}
                <li
                  className="truncate font-medium text-foreground"
                  aria-current="page"
                >
                  {active?.title ?? "Kỹ thuật viên"}
                </li>
              </ol>
            </nav>
            <div className="ml-auto flex items-center gap-3">
              <CurrentDateTime />
              <Separator orientation="vertical" className="h-4" />
              <Link
                href="/imaging/notifications"
                className={cn(
                  buttonVariants({ variant: "ghost", size: "icon" }),
                  "relative",
                )}
                aria-label={
                  unread > 0 ? `Thông báo, ${unread} chưa đọc` : "Thông báo"
                }
              >
                <Bell aria-hidden />
                <UnreadCountBadge count={unread} />
              </Link>
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
