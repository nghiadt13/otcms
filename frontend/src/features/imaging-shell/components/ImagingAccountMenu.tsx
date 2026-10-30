"use client";
import Image from "next/image";
import Link from "next/link";
import {
  SidebarFooter,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar";
import { useImaging } from "@/features/imaging/hooks/useImaging";

export function ImagingAccountMenu() {
  const { state } = useImaging();
  return (
    <SidebarFooter>
      <SidebarMenu>
        <SidebarMenuItem>
          <SidebarMenuButton
            size="lg"
            render={<Link href="/imaging/profile" />}
            tooltip={state.profile.name}
          >
            <span className="flex size-7 shrink-0 items-center justify-center overflow-hidden rounded-full bg-secondary text-xs font-semibold text-secondary-foreground">
              {state.profile.avatar ? (
                <Image
                  src={state.profile.avatar}
                  alt="Ảnh đại diện"
                  width={28}
                  height={28}
                  unoptimized
                />
              ) : (
                "MA"
              )}
            </span>
            <span className="grid text-left">
              <span className="font-medium">{state.profile.name}</span>
              <span className="text-xs text-muted-foreground">
                Kỹ thuật viên CĐHA
              </span>
            </span>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarFooter>
  );
}
