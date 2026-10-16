"use client";

import { useRouter } from "next/navigation";
import { ChevronsUpDown, LogOut } from "lucide-react";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar";
import { getSupabaseBrowserClient } from "@/lib/supabase/browser";
import { currentAdmin, getInitials } from "../currentAdmin";

function AccountSummary() {
  return (
    <>
      <Avatar className="size-8 rounded-lg">
        {currentAdmin.avatarUrl ? (
          <AvatarImage src={currentAdmin.avatarUrl} alt={currentAdmin.name} />
        ) : null}
        <AvatarFallback className="rounded-lg">
          {getInitials(currentAdmin.name)}
        </AvatarFallback>
      </Avatar>
      <span className="grid flex-1 text-left leading-tight">
        <span className="truncate text-sm font-medium">
          {currentAdmin.name}
        </span>
        <span className="truncate text-xs text-muted-foreground">
          {currentAdmin.email}
        </span>
      </span>
    </>
  );
}

export function AdminAccountMenu() {
  const router = useRouter();
  const { isMobile } = useSidebar();

  async function signOut() {
    try {
      await getSupabaseBrowserClient().auth.signOut();
    } catch {
      // Chưa cấu hình Supabase hoặc chưa đăng nhập: vẫn quay về trang đăng nhập.
    }
    router.push("/login");
  }

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger
            render={<SidebarMenuButton size="lg" tooltip={currentAdmin.name} />}
          >
            <AccountSummary />
            <ChevronsUpDown className="ml-auto size-4" />
          </DropdownMenuTrigger>
          <DropdownMenuContent
            className="w-64"
            side={isMobile ? "bottom" : "right"}
            align="end"
            sideOffset={8}
          >
            <div className="flex items-center gap-2 px-1.5 py-1.5">
              <AccountSummary />
            </div>
            <p className="px-1.5 pb-1.5 text-xs text-muted-foreground">
              Vai trò: {currentAdmin.roleLabel}
            </p>
            <DropdownMenuSeparator />
            <DropdownMenuItem variant="destructive" onClick={signOut}>
              <LogOut /> Đăng xuất
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  );
}
