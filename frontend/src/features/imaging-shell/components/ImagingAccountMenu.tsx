"use client";

import Link from "next/link";
import {
  ChevronsUpDown,
  KeyRound,
  LogOut,
  RefreshCw,
  UserRound,
} from "lucide-react";
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
import { useImaging } from "@/features/imaging/hooks/useImaging";

// Ô tài khoản cuối sidebar, cùng dạng với khu admin: avatar + tên + vai trò, bấm mở menu.
function AccountSummary() {
  const { profile } = useImaging().state;
  return (
    <>
      <Avatar className="size-8 rounded-lg">
        {profile.avatar ? (
          <AvatarImage src={profile.avatar} alt={profile.name} />
        ) : null}
        <AvatarFallback className="rounded-lg">
          {profile.username.slice(0, 2).toUpperCase()}
        </AvatarFallback>
      </Avatar>
      <span className="grid flex-1 text-left leading-tight">
        <span className="truncate text-sm font-medium">{profile.username}</span>
        <span className="truncate text-xs text-muted-foreground">
          Kỹ thuật viên CĐHA
        </span>
      </span>
    </>
  );
}

export function ImagingAccountMenu({
  onReset,
  onExit,
}: {
  onReset: () => void;
  onExit: () => void;
}) {
  const { isMobile, setOpenMobile } = useSidebar();
  const { profile } = useImaging().state;
  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger
            render={<SidebarMenuButton size="lg" tooltip={profile.username} />}
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
            <DropdownMenuSeparator />
            <DropdownMenuItem
              render={<Link href="/imaging/profile" />}
              onClick={() => setOpenMobile(false)}
            >
              <UserRound /> Hồ sơ tài khoản
            </DropdownMenuItem>
            <DropdownMenuItem
              render={<Link href="/imaging/change-password" />}
              onClick={() => setOpenMobile(false)}
            >
              <KeyRound /> Đổi mật khẩu
            </DropdownMenuItem>
            <DropdownMenuItem onClick={onReset}>
              <RefreshCw /> Khôi phục dữ liệu mẫu
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem variant="destructive" onClick={onExit}>
              <LogOut /> Đăng xuất
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  );
}
