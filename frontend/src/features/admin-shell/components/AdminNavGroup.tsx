"use client";

import Link from "next/link";
import { ChevronRight } from "lucide-react";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import {
  SidebarGroup,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar";
import { cn } from "@/lib/utils";
import type {
  AdminNavGroup as AdminNavGroupData,
  AdminNavItem,
} from "../adminNavigation";

// Mục đang mở: nền màu nhấn + chữ đậm + icon đổi màu + thanh dọc ở mép trái.
const activeItemClass = cn(
  "relative data-active:bg-teal-50 data-active:font-semibold data-active:text-teal-800",
  "data-active:hover:bg-teal-100 data-active:[&_svg]:text-teal-700",
  "dark:data-active:bg-teal-950 dark:data-active:text-teal-200 dark:data-active:hover:bg-teal-900",
  "dark:data-active:[&_svg]:text-teal-300",
  "data-active:before:absolute data-active:before:inset-y-1.5 data-active:before:left-0",
  "data-active:before:w-[3px] data-active:before:rounded-full data-active:before:bg-teal-600",
);

type AdminNavGroupProps = {
  group: AdminNavGroupData;
  activeHref: string | undefined;
  open: boolean;
  onOpenChange: (open: boolean) => void;
};

function NavLink({ item, active }: { item: AdminNavItem; active: boolean }) {
  return (
    <SidebarMenuItem>
      <SidebarMenuButton
        isActive={active}
        render={<Link href={item.href} />}
        tooltip={item.title}
        className={activeItemClass}
      >
        <item.icon />
        <span>{item.title}</span>
      </SidebarMenuButton>
    </SidebarMenuItem>
  );
}

// Nhóm `standalone` hiện thành liên kết cấp 1. Nhóm thường có tiêu đề nhỏ (không
// icon) để bấm thu gọn; icon nằm ở từng mục con vì đó mới là thứ được bấm.
export function AdminNavGroup({
  group,
  activeHref,
  open,
  onOpenChange,
}: AdminNavGroupProps) {
  if (group.standalone) {
    return (
      <SidebarGroup>
        <SidebarMenu>
          {group.items.map((item) => (
            <NavLink
              key={item.href}
              item={item}
              active={activeHref === item.href}
            />
          ))}
        </SidebarMenu>
      </SidebarGroup>
    );
  }

  return (
    <SidebarGroup className="py-1">
      <Collapsible open={open} onOpenChange={onOpenChange}>
        {/* Khi sidebar thu thành icon, ẩn tiêu đề và luôn hiện các mục con. */}
        <CollapsibleTrigger className="flex w-full items-center justify-between rounded-md px-2 py-1.5 text-[11px] font-semibold tracking-wider text-muted-foreground uppercase transition-colors hover:text-foreground group-data-[collapsible=icon]:hidden">
          <span>{group.label}</span>
          <ChevronRight
            className={cn(
              "size-3.5 transition-transform duration-200",
              open && "rotate-90",
            )}
          />
        </CollapsibleTrigger>
        <CollapsibleContent
          keepMounted
          className="group-data-[collapsible=icon]:block!"
        >
          <SidebarMenu className="mx-3.5 mt-0.5 border-l border-sidebar-border pl-2.5 group-data-[collapsible=icon]:mx-0 group-data-[collapsible=icon]:mt-0 group-data-[collapsible=icon]:border-0 group-data-[collapsible=icon]:pl-0">
            {group.items.map((item) => (
              <NavLink
                key={item.href}
                item={item}
                active={activeHref === item.href}
              />
            ))}
          </SidebarMenu>
        </CollapsibleContent>
      </Collapsible>
    </SidebarGroup>
  );
}
