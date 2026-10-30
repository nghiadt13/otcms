"use client";
import { useState, type ReactNode } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Bone, ShieldCheck, LogOut } from "lucide-react";
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

export function OwnerShell({ children }: { children: ReactNode }) {
  const router = useRouter();
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
                render={<Link href="/owner/accounts" />}
                tooltip="OTCMS Chủ phòng khám"
              >
                <span className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                  <Bone className="size-4" />
                </span>
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
            <SidebarMenuItem>
              <SidebarMenuButton
                isActive
                render={<Link href="/owner/accounts" />}
                tooltip="Tài khoản Admin"
              >
                <ShieldCheck />
                <span>Tài khoản Admin</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
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
            Chủ phòng khám / Tài khoản Admin
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
