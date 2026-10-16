"use client";

import { usePathname } from "next/navigation";
import { Separator } from "@/components/ui/separator";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { NotificationBell } from "@/features/notifications/components/NotificationBell";
import { findActiveNavItem } from "../adminNavigation";
import { CurrentDateTime } from "./CurrentDateTime";

export function AdminHeader() {
  const item = findActiveNavItem(usePathname());

  return (
    <header className="flex h-14 shrink-0 items-center gap-2 border-b px-4">
      <SidebarTrigger className="-ml-1" />
      <Separator orientation="vertical" className="mr-2 h-4" />
      <span className="text-sm font-medium">{item?.title ?? "Quản trị"}</span>
      <div className="ml-auto flex items-center gap-3">
        <CurrentDateTime />
        <Separator orientation="vertical" className="h-4" />
        <NotificationBell />
      </div>
    </header>
  );
}
