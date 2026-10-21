"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ChevronsUpDown, KeyRound, LogOut, UserRound } from "lucide-react";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Skeleton } from "@/components/ui/skeleton";
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar";
import { ROLE_LABELS } from "@/lib/domain/clinicRoles";
import { ADMIN_ROUTES } from "@/lib/routes/adminRoutes";
import { getSupabaseBrowserClient } from "@/lib/supabase/browser";
import { ChangePasswordDialog } from "@/features/my-account/components/ChangePasswordDialog";
import { useMyAccount } from "@/features/my-account/hooks/useMyAccount";
import type { MyAccount } from "@/features/my-account/types/myAccount";

// Admin không có email (BR-11): hiện tên đăng nhập và vai trò.
function AccountSummary({ account }: { account: MyAccount | null }) {
  return (
    <>
      <Avatar className="size-8 rounded-lg">
        {account?.avatarUrl ? (
          <AvatarImage src={account.avatarUrl} alt={account.username} />
        ) : null}
        <AvatarFallback className="rounded-lg">
          {account ? account.username.slice(0, 2).toUpperCase() : ""}
        </AvatarFallback>
      </Avatar>
      <span className="grid flex-1 text-left leading-tight">
        {account ? (
          <>
            <span className="truncate text-sm font-medium">
              {account.username}
            </span>
            <span className="truncate text-xs text-muted-foreground">
              {ROLE_LABELS[account.role]}
            </span>
          </>
        ) : (
          <>
            <Skeleton className="h-3.5 w-24" />
            <Skeleton className="mt-1 h-3 w-16" />
          </>
        )}
      </span>
    </>
  );
}

export function AdminAccountMenu() {
  const router = useRouter();
  const { isMobile } = useSidebar();
  const { account } = useMyAccount();
  // Dialog nằm ngoài DropdownMenuContent: để trong menu thì bị unmount khi menu đóng.
  const [passwordOpen, setPasswordOpen] = useState(false);

  async function signOut() {
    try {
      await getSupabaseBrowserClient().auth.signOut();
    } catch {
      // Chưa cấu hình Supabase hoặc chưa đăng nhập: vẫn quay về trang đăng nhập.
    }
    // replace: nút Back không đưa người dùng quay lại khu admin.
    router.replace("/login");
  }

  return (
    <>
      <SidebarMenu>
        <SidebarMenuItem>
          <DropdownMenu>
            <DropdownMenuTrigger
              render={
                <SidebarMenuButton
                  size="lg"
                  tooltip={account?.username ?? "Tài khoản"}
                />
              }
            >
              <AccountSummary account={account} />
              <ChevronsUpDown className="ml-auto size-4" />
            </DropdownMenuTrigger>
            <DropdownMenuContent
              className="w-64"
              side={isMobile ? "bottom" : "right"}
              align="end"
              sideOffset={8}
            >
              <div className="flex items-center gap-2 px-1.5 py-1.5">
                <AccountSummary account={account} />
              </div>
              <DropdownMenuSeparator />
              <DropdownMenuItem render={<Link href={ADMIN_ROUTES.account} />}>
                <UserRound /> Hồ sơ tài khoản
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => setPasswordOpen(true)}>
                <KeyRound /> Đổi mật khẩu
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem variant="destructive" onClick={signOut}>
                <LogOut /> Đăng xuất
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </SidebarMenuItem>
      </SidebarMenu>
      <ChangePasswordDialog open={passwordOpen} onOpenChange={setPasswordOpen} />
    </>
  );
}
