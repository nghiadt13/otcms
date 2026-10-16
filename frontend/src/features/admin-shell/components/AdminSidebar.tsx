"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from "@/components/ui/sidebar";
import { adminNavGroups, findActiveNavItem } from "../adminNavigation";
import { AdminAccountMenu } from "./AdminAccountMenu";
import { AdminNavGroup } from "./AdminNavGroup";

export function AdminSidebar() {
  const pathname = usePathname();
  const activeHref = findActiveNavItem(pathname)?.href;
  // Trạng thái do người dùng tự bấm; chưa bấm thì nhóm chứa trang hiện tại tự mở.
  const [userOpen, setUserOpen] = useState<Record<string, boolean>>({});

  const isGroupOpen = (label: string, hasActive: boolean) =>
    userOpen[label] ?? hasActive;

  const handleOpenChange = (label: string, open: boolean) =>
    setUserOpen((current) => ({ ...current, [label]: open }));

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              size="lg"
              render={<Link href="/admin" />}
              tooltip="OTCMS Quản trị"
            >
              <span className="flex size-8 items-center justify-center rounded-lg bg-primary text-sm font-bold text-primary-foreground">
                OT
              </span>
              <span className="grid flex-1 text-left leading-tight">
                <span className="truncate font-semibold">OTCMS</span>
                <span className="truncate text-xs text-muted-foreground">
                  Khu vực quản trị
                </span>
              </span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      <SidebarContent>
        {adminNavGroups.map((group) => (
          <AdminNavGroup
            key={group.label}
            group={group}
            activeHref={activeHref}
            open={isGroupOpen(
              group.label,
              group.items.some((item) => item.href === activeHref),
            )}
            onOpenChange={(open) => handleOpenChange(group.label, open)}
          />
        ))}
      </SidebarContent>

      <SidebarFooter>
        <AdminAccountMenu />
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  );
}
