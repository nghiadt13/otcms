"use client";

import Link from "next/link";
import {
  ChevronsUpDown,
  KeyRound,
  LogOut,
  RefreshCw,
  UserRound,
} from "lucide-react";
import { useEffect, useState } from "react";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import {
  ACCOUNT_AVATAR_CHANGED_EVENT,
  loadAccountAvatarUrl,
} from "@/lib/auth/accountAvatarCache";
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
import { fetchCurrentAccount, type CurrentAccount } from "@/lib/auth/currentAccountApi";
import { ROLE_LABELS, type ActorRole } from "@/lib/domain/clinicRoles";

// Ô tài khoản cuối sidebar, cùng dạng với khu admin: avatar + tên + vai trò, bấm mở menu.
function AccountSummary({ account }: { account: CurrentAccount | null }) {
  const name = account?.username || account?.displayName;
  const role = account?.role && account.role in ROLE_LABELS
    ? ROLE_LABELS[account.role as ActorRole]
    : account?.accountType === "IMAGING_TECHNICIAN"
      ? ROLE_LABELS.IMAGING_TECHNICIAN
      : null;
  return (
    <>
      <Avatar className="size-8 rounded-lg">
        {account?.avatarUrl ? (
          <AvatarImage src={account.avatarUrl} alt={name ?? "Tài khoản"} />
        ) : null}
        <AvatarFallback className="rounded-lg">
          {(name ?? "").slice(0, 2).toUpperCase()}
        </AvatarFallback>
      </Avatar>
      <span className="grid flex-1 text-left leading-tight">
        {name ? (
          <span className="truncate text-sm font-medium">{name}</span>
        ) : (
          <Skeleton className="h-3.5 w-24" />
        )}
        {role ? (
          <span className="truncate text-xs text-muted-foreground">{role}</span>
        ) : (
          <Skeleton className="mt-1 h-3 w-20" />
        )}
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
  const [account, setAccount] = useState<CurrentAccount | null>(null);

  useEffect(() => {
    let active = true;
    const load = () => {
      fetchCurrentAccount()
        .then(async (current) => {
          const avatarUrl = current?.avatarUrl
            ? await loadAccountAvatarUrl(current.id)
            : null;
          if (active) setAccount(current ? { ...current, avatarUrl } : null);
        })
        .catch(() => {
          if (active) setAccount(null);
        });
    };
    load();
    window.addEventListener(ACCOUNT_AVATAR_CHANGED_EVENT, load);
    return () => {
      active = false;
      window.removeEventListener(ACCOUNT_AVATAR_CHANGED_EVENT, load);
    };
  }, []);

  const accountName = account?.username || account?.displayName || "Tài khoản";
  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger
            render={<SidebarMenuButton size="lg" tooltip={accountName} />}
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
