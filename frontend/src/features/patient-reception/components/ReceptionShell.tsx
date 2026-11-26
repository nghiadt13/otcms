"use client";

import { OtcmsBrandMark } from "@/components/brand/OtcmsBrandMark";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Bell, ChevronRight } from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";
import { signOut } from "@/lib/auth/authSession";
import { apiRequest } from "@/lib/api/client";
import { CurrentDateTime } from "@/components/shell/CurrentDateTime";
import {
  SidebarNavGroup,
  type SidebarNavGroupData,
} from "@/components/shell/SidebarNavGroup";
import { UnreadCountBadge } from "@/components/shell/UnreadCountBadge";
import { buttonVariants } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
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
import { Toaster } from "@/components/ui/sonner";
import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import { cn } from "@/lib/utils";
import {
  receptionHref,
  receptionNavigation,
} from "../navigation";
import { ReceptionAccountMenu } from "./ReceptionAccountMenu";
import { ReceptionProvider } from "./ReceptionProvider";

// Nhóm menu theo `group` của navigation.ts, cùng dạng sidebar khu admin (SidebarNavGroup).
const navGroups: SidebarNavGroupData[] = [
  ...new Set(receptionNavigation.map((item) => item.group)),
].map((label) => ({
  label,
  items: receptionNavigation
    .filter((item) => item.group === label)
    .map((item) => ({
      title: item.title,
      href: receptionHref(item.section),
      icon: item.icon,
    })),
}));

function Menu({ onExit }: { onExit: () => void }) {
  const path = usePathname();
  const { setOpenMobile } = useSidebar();
  // Trạng thái do người dùng tự bấm; chưa bấm thì mọi nhóm mặc định mở (như admin).
  const [userOpen, setUserOpen] = useState<Record<string, boolean>>({});
  return (
    <Sidebar collapsible="icon" className="reception-no-print">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              size="lg"
              render={<Link href="/reception" />}
              tooltip="OTCMS Lễ tân"
            >
              <OtcmsBrandMark className="size-8" />
              <span className="grid flex-1 text-left leading-tight">
                <span className="truncate font-semibold">OTCMS</span>
                <span className="truncate text-xs text-muted-foreground">
                  Khu vực lễ tân
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
            activeHref={path}
            open={userOpen[group.label] ?? true}
            onOpenChange={(open) =>
              setUserOpen((current) => ({ ...current, [group.label]: open }))
            }
            onNavigate={() => setOpenMobile(false)}
          />
        ))}
      </SidebarContent>
      <SidebarFooter>
        <ReceptionAccountMenu onExit={onExit} />
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  );
}
function Frame({ children }: { children: ReactNode }) {
  const router = useRouter();
  const path = usePathname();
  const [unread, setUnread] = useState(0);
  const [notificationRevision, setNotificationRevision] = useState(0);
  const [exitOpen, setExitOpen] = useState(false);
  const item = receptionNavigation.find(
    (item) => receptionHref(item.section) === path,
  );
  useEffect(() => {
    const changed = () => setNotificationRevision((revision) => revision + 1);
    window.addEventListener("notifications:changed", changed);
    return () => window.removeEventListener("notifications:changed", changed);
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    apiRequest<{ unreadCount: number }>("/api/v1/me/notifications/unread-count", {
      signal: controller.signal,
    })
      .then((result) => {
        if (!controller.signal.aborted) setUnread(result.unreadCount);
      })
      .catch(() => {
        // Keep the last count if the notification service is temporarily unavailable.
      });
    return () => controller.abort();
  }, [path, notificationRevision]);
  return (
    <SidebarProvider>
      <Menu onExit={() => setExitOpen(true)} />
      <SidebarInset className="min-w-0">
        <header className="reception-no-print flex h-14 shrink-0 items-center gap-2 border-b px-4">
          <SidebarTrigger className="-ml-1" />
          <Separator orientation="vertical" className="mr-2 h-4" />
          <nav aria-label="Breadcrumb" className="flex min-w-0 items-center">
            <ol className="flex min-w-0 items-center gap-1.5 text-xs sm:text-sm">
              {item && (
                <>
                  <li className="hidden items-center text-muted-foreground sm:inline-flex">
                    {item.group}
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
                {item?.title ?? "Lễ tân"}
              </li>
            </ol>
          </nav>
          <div className="ml-auto flex items-center gap-3">
            <CurrentDateTime />
            <Separator orientation="vertical" className="h-4" />
            <Link
              href={receptionHref("notifications")}
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
        <main id="reception-main" className="min-w-0 flex-1 p-4 text-sm md:p-6">
          {children}
        </main>
      </SidebarInset>
      <Toaster position="bottom-right" />
      <ConfirmActionDialog
        open={exitOpen}
        onOpenChange={setExitOpen}
        title="Đăng xuất khỏi OTCMS?"
        description="Bạn sẽ quay về màn hình đăng nhập nhân viên."
        confirmLabel="Đăng xuất"
        pendingLabel="Đang đăng xuất..."
        onConfirm={async () => {
          await signOut();
          router.replace("/staff-login");
        }}
      />
    </SidebarProvider>
  );
}
export function ReceptionShell({ children }: { children: ReactNode }) {
  return (
    <ReceptionProvider>
      <Frame>{children}</Frame>
    </ReceptionProvider>
  );
}
