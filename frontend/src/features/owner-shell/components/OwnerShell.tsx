"use client";
import { useState, type ReactNode } from "react";
import { OtcmsBrandMark } from "@/components/brand/OtcmsBrandMark";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { ChartColumn, ShieldCheck, LogOut, Wallet } from "lucide-react";
import {
  SidebarProvider,
  Sidebar,
  SidebarHeader,
  SidebarContent,
  SidebarFooter,
  SidebarInset,
  SidebarMenu,
  SidebarMenuItem,
  SidebarMenuButton,
  SidebarTrigger,
  SidebarRail,
} from "@/components/ui/sidebar";
import { Button } from "@/components/ui/button";
import { Toaster } from "@/components/ui/sonner";
import { CurrentDateTime } from "@/components/shell/CurrentDateTime";
import { signOut } from "@/lib/auth/authSession";

const NAV_ITEMS = [
  {
    href: "/owner/dashboard",
    label: "Tổng quan kinh doanh",
    icon: ChartColumn,
  },
  { href: "/owner/finance", label: "Tài chính", icon: Wallet },
  { href: "/owner/accounts", label: "Tài khoản Admin", icon: ShieldCheck },
];

export function OwnerShell({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const current = NAV_ITEMS.find((item) => pathname.startsWith(item.href));
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  return (
    <SidebarProvider>
      <Sidebar collapsible="icon">
        <SidebarHeader>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton
                size="lg"
                render={<Link href="/owner/dashboard" />}
                tooltip="OTCMS Chủ phòng khám"
              >
                <OtcmsBrandMark className="size-8" />
                <span className="grid flex-1 text-left leading-tight">
                  <span className="font-semibold">OTCMS</span>
                  <span className="text-xs text-muted-foreground">
                    Chủ phòng khám
                  </span>
                </span>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarHeader>
        <SidebarContent>
          <SidebarMenu className="p-2">
            {NAV_ITEMS.map(({ href, label, icon: Icon }) => (
              <SidebarMenuItem key={href}>
                <SidebarMenuButton
                  isActive={current?.href === href}
                  render={<Link href={href} />}
                  tooltip={label}
                >
                  <Icon />
                  <span>{label}</span>
                </SidebarMenuButton>
              </SidebarMenuItem>
            ))}
          </SidebarMenu>
        </SidebarContent>
        <SidebarFooter>
          {error && (
            <p role="alert" className="text-xs text-destructive">
              {error}
            </p>
          )}
          <Button
            variant="ghost"
            disabled={pending}
            onClick={async () => {
              setPending(true);
              setError("");
              try {
                await signOut();
                router.replace("/staff-login");
              } catch {
                setError("Không thể đăng xuất. Hãy thử lại.");
                setPending(false);
              }
            }}
          >
            <LogOut className="size-4" />
            <span className="group-data-[collapsible=icon]:hidden">
              {pending ? "Đang đăng xuất…" : "Đăng xuất"}
            </span>
          </Button>
        </SidebarFooter>
        <SidebarRail />
      </Sidebar>
      <SidebarInset>
        <header className="flex h-14 shrink-0 items-center gap-3 border-b px-4">
          <SidebarTrigger />
          <span className="text-sm font-medium">
            Chủ phòng khám{current ? ` / ${current.label}` : ""}
          </span>
          <div className="ml-auto hidden md:block">
            <CurrentDateTime />
          </div>
        </header>
        <main className="min-w-0 flex-1 p-4 md:p-6">{children}</main>
      </SidebarInset>
      <Toaster position="bottom-right" />
    </SidebarProvider>
  );
}
