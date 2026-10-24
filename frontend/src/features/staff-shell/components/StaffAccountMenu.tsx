"use client";

import { ChevronDown, LogOut } from "lucide-react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { ROLE_LABELS } from "@/lib/domain/clinicRoles";
import { getMockCurrentActor } from "@/lib/session/mockCurrentActor";
import { getSupabaseBrowserClient } from "@/lib/supabase/browser";

// Menu tài khoản tối giản: tên, vai trò và Đăng xuất. Tên lấy từ người đăng nhập giả
// (xóa khi có /api/v1/me).
export function StaffAccountMenu() {
  const router = useRouter();
  const actor = getMockCurrentActor("staff");
  const roleLabel = ROLE_LABELS[actor.role];

  async function signOut() {
    try {
      await getSupabaseBrowserClient().auth.signOut();
    } catch {
      // Chưa cấu hình Supabase hoặc chưa đăng nhập: vẫn quay về trang đăng nhập.
    }
    // replace: nút Back không đưa người dùng quay lại khu Lễ tân.
    router.replace("/login");
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={<Button variant="ghost" size="sm" className="gap-2" />}
      >
        <span className="grid text-left leading-tight">
          <span className="text-sm font-medium">{actor.displayName}</span>
          <span className="hidden text-xs text-muted-foreground md:inline">
            {roleLabel}
          </span>
        </span>
        <ChevronDown aria-hidden className="size-4 text-muted-foreground" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuGroup>
          <DropdownMenuLabel className="flex flex-col gap-0.5 font-normal">
            <span className="text-sm font-medium text-foreground">
              {actor.displayName}
            </span>
            <span className="text-xs text-muted-foreground">{roleLabel}</span>
          </DropdownMenuLabel>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuItem variant="destructive" onClick={signOut}>
          <LogOut /> Đăng xuất
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
