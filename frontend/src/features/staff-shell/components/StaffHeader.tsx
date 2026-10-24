"use client";

import { usePathname } from "next/navigation";
import { Separator } from "@/components/ui/separator";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { getMockCurrentActor } from "@/lib/session/mockCurrentActor";
import {
  filterStaffNavItemsByRole,
  findActiveStaffNavItem,
  STAFF_NAV_ITEMS,
} from "../staffNavigation";
import { CurrentDateTime } from "./CurrentDateTime";
import { StaffAccountMenu } from "./StaffAccountMenu";

// Header khu Lễ tân: nút mở sidebar, tên mục đang mở, giờ hiện tại và menu tài khoản.
// Không có chuông thông báo (chưa có nhu cầu ở khu này).
export function StaffHeader() {
  const pathname = usePathname();
  const role = getMockCurrentActor("staff").role;
  const visibleItems = filterStaffNavItemsByRole(STAFF_NAV_ITEMS, role);
  const title = findActiveStaffNavItem(pathname, visibleItems)?.title ?? "Khu Lễ tân";

  return (
    <header className="flex h-14 shrink-0 items-center gap-2 border-b px-4">
      <SidebarTrigger className="-ml-1" />
      <Separator orientation="vertical" className="mr-2 h-4" />
      <p className="truncate text-sm font-medium">{title}</p>

      <div className="ml-auto flex items-center gap-3">
        <CurrentDateTime />
        <Separator orientation="vertical" className="h-4" />
        <StaffAccountMenu />
      </div>
    </header>
  );
}
