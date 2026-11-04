"use client";

import { ArrowLeft, Bone } from "lucide-react";
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
import { SidebarNavGroup } from "@/components/shell/SidebarNavGroup";
import { DOCTOR_ROUTES } from "@/lib/routes/doctorRoutes";
import { doctorNavGroups, findActiveNavItem } from "../doctorNavigation";
import {
  buildVisitSidebarGroups,
  parseVisitPath,
  visitHref,
} from "../doctorVisitNavigation";
import { DoctorAccountMenu } from "./DoctorAccountMenu";

export function DoctorSidebar() {
  const pathname = usePathname();
  // Mở một lượt khám thì sidebar đổi sang các màn của lượt khám đó (R3, màn 8–27).
  const visit = parseVisitPath(pathname);
  const groups = visit ? buildVisitSidebarGroups(visit.visitId) : doctorNavGroups;
  const activeHref = visit
    ? visitHref(visit.visitId, visit.section)
    : findActiveNavItem(pathname)?.href;
  // Trạng thái do người dùng tự bấm; chưa bấm thì mọi nhóm mặc định mở.
  const [userOpen, setUserOpen] = useState<Record<string, boolean>>({});

  const isGroupOpen = (label: string) => userOpen[label] ?? true;

  const handleOpenChange = (label: string, open: boolean) =>
    setUserOpen((current) => ({ ...current, [label]: open }));

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              size="lg"
              render={<Link href={DOCTOR_ROUTES.dashboard} />}
              tooltip="OTCMS Bác sĩ"
            >
              <span className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                <Bone aria-hidden className="size-4" />
              </span>
              <span className="grid flex-1 text-left leading-tight">
                <span className="truncate font-semibold">OTCMS</span>
                <span className="truncate text-xs text-muted-foreground">
                  Bác sĩ chỉnh hình
                </span>
              </span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      <SidebarContent>
        {visit ? (
          <SidebarMenu className="px-2 pt-2">
            <SidebarMenuItem>
              <SidebarMenuButton
                render={<Link href={DOCTOR_ROUTES.todayPatients} />}
                tooltip="Về bệnh nhân hôm nay"
              >
                <ArrowLeft />
                <span>Bệnh nhân hôm nay</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        ) : null}
        {groups.map((group) => (
          <SidebarNavGroup
            key={group.label}
            group={group}
            activeHref={activeHref}
            open={isGroupOpen(group.label)}
            onOpenChange={(open) => handleOpenChange(group.label, open)}
          />
        ))}
      </SidebarContent>

      <SidebarFooter>
        <DoctorAccountMenu />
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  );
}
