"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Bell, Bone, ChevronRight } from "lucide-react";
import { useState, type ReactNode } from "react";
import { CurrentDateTime } from "@/components/shell/CurrentDateTime";
import {
  SidebarNavGroup,
  type SidebarNavGroupData,
} from "@/components/shell/SidebarNavGroup";
import { UnreadCountBadge } from "@/components/shell/UnreadCountBadge";
import { Button, buttonVariants } from "@/components/ui/button";
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
import { receptionHref, receptionNavigation } from "../navigation";
import { ReceptionAccountMenu } from "./ReceptionAccountMenu";
import { ReceptionProvider, useReception } from "./ReceptionProvider";

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
              <span className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                <Bone className="size-4" aria-hidden />
              </span>
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
  const { data, loading, pending, error, reload } = useReception();
  const path = usePathname();
  const [exitOpen, setExitOpen] = useState(false);
  const item = receptionNavigation.find(
    (item) => receptionHref(item.section) === path,
  );
  const unread = data?.notices.filter((n) => !n.read).length ?? 0;
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
            {data && <CurrentDateTime value={data.clock} />}
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
        <div className="reception-no-print flex flex-wrap items-center justify-between gap-2 border-b bg-secondary/50 px-4 py-2 text-xs text-muted-foreground md:px-6">
          <span>Bản xem thử giao diện · dữ liệu mẫu · giờ Việt Nam</span>
          <span>
            Tải lại trang sẽ đặt lại dữ liệu. Không nhập thông tin bệnh nhân
            thật.
          </span>
        </div>
        <main id="reception-main" className="min-w-0 flex-1 p-4 text-sm md:p-6">
          {error && (
            <div
              role="alert"
              className="reception-no-print mb-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-destructive/30 bg-card p-3 text-destructive"
            >
              <span>{error}</span>
              <Button
                variant="outline"
                disabled={loading || pending}
                onClick={() => void reload()}
              >
                Thử lại
              </Button>
            </div>
          )}
          {children}
        </main>
      </SidebarInset>
      <Toaster position="bottom-right" />
      <ConfirmActionDialog
        open={exitOpen}
        onOpenChange={setExitOpen}
        title="Thoát bản xem thử?"
        description="Các thao tác trên dữ liệu mẫu sẽ được đặt lại khi bạn mở lại khu vực lễ tân."
        confirmLabel="Thoát"
        pendingLabel="Đang thoát..."
        onConfirm={() => {
          router.push("/workspace");
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
