"use client";
import { OtcmsBrandMark } from "@/components/brand/OtcmsBrandMark";
import Link from "next/link";
import { usePathname } from "next/navigation";

import {
  Sidebar,
  SidebarContent,
  SidebarHeader,
  SidebarFooter,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from "@/components/ui/sidebar";
import { imagingNavGroups } from "../imagingNavigation";
import { ImagingNavGroup } from "./ImagingNavGroup";
import { ImagingAccountMenu } from "./ImagingAccountMenu";

export function ImagingSidebar({
  onReset,
  onExit,
  showDemoActions = true,
}: {
  onReset: () => void;
  onExit: () => void;
  showDemoActions?: boolean;
}) {
  const pathname = usePathname();

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              size="lg"
              render={<Link href="/imaging" />}
              tooltip="OTCMS · Chẩn đoán hình ảnh"
            >
              <OtcmsBrandMark className="size-8" />
              <span className="grid text-left">
                <span className="font-semibold">OTCMS</span>
                <span className="text-xs text-muted-foreground">
                  Khu vực kỹ thuật viên
                </span>
              </span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        {imagingNavGroups.map((group) => (
          <ImagingNavGroup
            key={group.label}
            group={group}
            activePath={pathname}
          />
        ))}
      </SidebarContent>
      <SidebarFooter>
        <ImagingAccountMenu
          onReset={onReset}
          onExit={onExit}
          showDemoActions={showDemoActions}
        />
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  );
}
