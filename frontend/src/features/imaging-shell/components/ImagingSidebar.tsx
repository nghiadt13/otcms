"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Bone } from "lucide-react";
import {
  Sidebar,
  SidebarContent,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from "@/components/ui/sidebar";
import { imagingNavGroups } from "../imagingNavigation";
import { ImagingNavGroup } from "./ImagingNavGroup";
import { ImagingAccountMenu } from "./ImagingAccountMenu";

export function ImagingSidebar() {
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
              <span className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                <Bone className="size-4" aria-hidden />
              </span>
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
      <ImagingAccountMenu />
      <SidebarRail />
    </Sidebar>
  );
}
