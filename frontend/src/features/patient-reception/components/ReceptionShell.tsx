"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Bell, Bone, LogOut, RefreshCw } from "lucide-react";
import { useState, type ReactNode } from "react";
import { Button, buttonVariants } from "@/components/ui/button";
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
import { Toaster } from "@/components/ui/sonner";
import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import { receptionHref, receptionNavigation } from "../navigation";
import { ReceptionProvider, useReception } from "./ReceptionProvider";

function Menu() {
  const path = usePathname();
  const { setOpenMobile } = useSidebar();
  return (
    <Sidebar collapsible="icon" className="reception-no-print">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              size="lg"
              render={<Link href="/reception" />}
              tooltip="OTCMS · Lễ tân"
            >
              <span className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                <Bone className="size-4" aria-hidden />
              </span>
              <span className="grid text-left">
                <span className="font-semibold">OTCMS</span>
                <span className="text-xs text-muted-foreground">
                  Khu vực lễ tân
                </span>
              </span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        {[...new Set(receptionNavigation.map((item) => item.group))].map(
          (group) => (
            <SidebarGroup key={group}>
              <SidebarGroupLabel>{group}</SidebarGroupLabel>
              <SidebarGroupContent>
                <SidebarMenu>
                  {receptionNavigation
                    .filter((item) => item.group === group)
                    .map((item) => (
                      <SidebarMenuItem key={item.section}>
                        <SidebarMenuButton
                          render={<Link href={receptionHref(item.section)} />}
                          tooltip={item.title}
                          isActive={path === receptionHref(item.section)}
                          onClick={() => setOpenMobile(false)}
                        >
                          <item.icon aria-hidden />
                          <span>{item.title}</span>
                        </SidebarMenuButton>
                      </SidebarMenuItem>
                    ))}
                </SidebarMenu>
              </SidebarGroupContent>
            </SidebarGroup>
          ),
        )}
      </SidebarContent>
      <SidebarFooter>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              render={<Link href="/reception/account" />}
              tooltip="Thiên Quang"
            >
              <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-secondary text-xs font-semibold text-secondary-foreground">
                TQ
              </span>
              <span className="grid text-left">
                <span className="font-medium">Thiên Quang</span>
                <span className="text-xs text-muted-foreground">
                  Lễ tân · tài khoản mẫu
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
function Frame({ children }: { children: ReactNode }) {
  const router = useRouter();
  const { data, loading, pending, error, reload } = useReception();
  const path = usePathname();
  const [exitOpen, setExitOpen] = useState(false);
  const item = receptionNavigation.find(
    (item) => receptionHref(item.section) === path,
  );
  return (
    <SidebarProvider>
      <Menu />
      <SidebarInset className="min-w-0">
        <header className="reception-no-print flex h-14 shrink-0 items-center gap-3 border-b px-4">
          <SidebarTrigger aria-label="Mở hoặc thu gọn menu" />
          <span className="truncate text-sm font-medium">
            {item?.title ?? "Lễ tân"}
          </span>
          <div className="ml-auto flex items-center gap-2">
            <span className="hidden text-xs text-muted-foreground md:block">
              {data?.clock.slice(0, 10).split("-").reverse().join("/")} · 09:10
            </span>
            <Button
              variant="ghost"
              size="icon"
              aria-label="Làm mới dữ liệu"
              disabled={loading || pending}
              onClick={() => void reload()}
            >
              <RefreshCw
                className={
                  loading ? "animate-spin motion-reduce:animate-none" : ""
                }
              />
            </Button>
            <Link
              href="/reception/notifications"
              className={buttonVariants({ variant: "ghost", size: "icon" })}
              aria-label={`Thông báo, ${data?.notices.filter((n) => !n.read).length ?? 0} chưa đọc`}
            >
              <Bell />
              <span className="sr-only">Thông báo</span>
            </Link>
            <Button
              variant="ghost"
              size="icon"
              aria-label="Thoát bản xem thử"
              onClick={() => setExitOpen(true)}
            >
              <LogOut />
            </Button>
          </div>
        </header>
        <div className="reception-no-print flex flex-wrap items-center justify-between gap-2 border-b bg-secondary/50 px-4 py-2 text-xs text-muted-foreground md:px-6">
          <span>Bản xem thử giao diện · dữ liệu mẫu · giờ mô phỏng 09:10</span>
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
