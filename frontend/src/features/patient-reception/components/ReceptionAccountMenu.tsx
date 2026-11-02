"use client";

import Link from "next/link";
import { ChevronsUpDown, LogOut, UserRound } from "lucide-react";
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
import { receptionHref } from "../navigation";
import type { ReceptionState } from "../types";
import { useReception } from "./ReceptionProvider";

// Ô tài khoản cuối sidebar, cùng dạng với khu admin: avatar + tên đăng nhập + vai trò, bấm mở menu.
function AccountSummary({
  account,
}: {
  account: ReceptionState["account"] | undefined;
}) {
  return (
    <>
      <Avatar className="size-8 rounded-lg">
        {account?.avatar ? (
          <AvatarImage src={account.avatar} alt={account.name} />
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
              Lễ tân
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

export function ReceptionAccountMenu({ onExit }: { onExit: () => void }) {
  const { isMobile, setOpenMobile } = useSidebar();
  const account = useReception().data?.account;
  return (
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
            <DropdownMenuItem
              render={<Link href={receptionHref("account")} />}
              onClick={() => setOpenMobile(false)}
            >
              <UserRound /> Tài khoản của tôi
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem variant="destructive" onClick={onExit}>
              <LogOut /> Thoát bản xem thử
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  );
}
