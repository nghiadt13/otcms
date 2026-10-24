"use client";

import { Bone } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from "@/components/ui/sidebar";
import { STAFF_ROUTES } from "@/lib/routes/staffRoutes";
import { getMockCurrentActor } from "@/lib/session/mockCurrentActor";
import {
  filterStaffNavItemsByRole,
  findActiveStaffNavItem,
  groupStaffNavItems,
  STAFF_NAV_ITEMS,
} from "../staffNavigation";

// Sidebar khu Lễ tân: nhóm tiêu đề luôn mở, không thu gọn từng nhóm (menu ngắn, cần thấy
// hết để chuyển việc nhanh). Mục chỉ hiện khi vai trò được phép.
export function StaffSidebar() {
  const pathname = usePathname();
  const role = getMockCurrentActor("staff").role;
  const visibleItems = filterStaffNavItemsByRole(STAFF_NAV_ITEMS, role);
  const groups = groupStaffNavItems(visibleItems);
  const activeHref = findActiveStaffNavItem(pathname, visibleItems)?.href;

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              size="lg"
              render={<Link href={STAFF_ROUTES.home} />}
              tooltip="OTCMS Lễ tân"
            >
              <span className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                <Bone aria-hidden className="size-4" />
              </span>
              <span className="grid flex-1 text-left leading-tight">
                <span className="truncate font-semibold">OTCMS</span>
                <span className="truncate text-xs text-muted-foreground">
                  Khu Lễ tân
                </span>
              </span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      <SidebarContent>
        {groups.map(({ group, items }) => (
          <SidebarGroup key={group}>
            <SidebarGroupLabel>{group}</SidebarGroupLabel>
            <SidebarMenu>
              {items.map((item) => (
                <SidebarMenuItem key={item.href}>
                  <SidebarMenuButton
                    isActive={item.href === activeHref}
                    render={<Link href={item.href} />}
                    tooltip={item.title}
                  >
                    <item.icon />
                    <span>{item.title}</span>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroup>
        ))}
      </SidebarContent>

      <SidebarRail />
    </Sidebar>
  );
}
