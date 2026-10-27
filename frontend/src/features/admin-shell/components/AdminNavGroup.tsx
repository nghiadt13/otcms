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

// Mục đang mở: nền màu nhấn + chữ đậm + icon đổi màu + thanh dọc teal ở mép trái.
const activeItemClass = cn(
  "relative data-active:bg-sidebar-accent data-active:font-semibold data-active:text-sidebar-accent-foreground",
  "data-active:hover:bg-brand-muted data-active:[&_svg]:text-brand",
  "dark:data-active:hover:bg-brand-hover/40",
  "dark:data-active:[&_svg]:text-brand-bright",
  "data-active:before:absolute data-active:before:inset-y-1.5 data-active:before:left-0",
  "data-active:before:w-[3px] data-active:before:rounded-full data-active:before:bg-brand dark:data-active:before:bg-brand-bright",
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
              "size-3.5 transition-transform duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] motion-reduce:transition-none",
              open && "rotate-90",
            )}
          />
        </CollapsibleTrigger>
        {/* Mở/đóng mượt: Base UI đo chiều cao thật vào --collapsible-panel-height,
            chiều cao chạy từ 0 khi mở (starting-style) và về 0 khi đóng (data-closed),
            kèm mờ dần; ẩn hẳn (invisible) để không tab vào link bị thu. Khi sidebar thu thành icon thì bỏ giới hạn. */}
        <CollapsibleContent
          keepMounted
          className={cn(
            "h-(--collapsible-panel-height) overflow-hidden opacity-100",
            "transition-[height,opacity,visibility] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)]",
            "data-closed:invisible data-closed:h-0 data-closed:opacity-0 data-starting-style:h-0 data-starting-style:opacity-0",
            "motion-reduce:transition-none",
            "group-data-[collapsible=icon]:block! group-data-[collapsible=icon]:h-auto! group-data-[collapsible=icon]:opacity-100! group-data-[collapsible=icon]:visible!",
          )}
        >
          <SidebarMenu className="mx-3.5 mt-0.5 w-auto gap-1 border-l border-sidebar-border pl-2.5 group-data-[collapsible=icon]:mx-0 group-data-[collapsible=icon]:mt-0 group-data-[collapsible=icon]:border-0 group-data-[collapsible=icon]:pl-0">
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
