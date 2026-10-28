"use client";

import { ChevronDown, LogOut } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { signOut as endSession } from "@/lib/auth/authSession";
import type { PatientAccountLink } from "../patientNavigation";

/** Chữ cái đầu của họ và tên, dùng làm ảnh đại diện tạm. */
function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  const first = parts[0]?.charAt(0) ?? "";
  const last = parts.length > 1 ? parts[parts.length - 1].charAt(0) : "";
  return (first + last).toUpperCase();
}

// Ô tài khoản góc phải: tên hiện từ màn sm trở lên, còn lại là ảnh đại diện nhưng vẫn có
// `aria-label` nêu tên. Menu có tên và vai trò, các mục tài khoản, rồi Đăng xuất.
export function PatientAccountMenu({
  displayName,
  roleLabel,
  links,
}: {
  displayName: string;
  roleLabel: string;
  links: readonly PatientAccountLink[];
}) {
  const router = useRouter();

  async function signOut() {
    // endSession bỏ qua lỗi mạng và luôn xoá phiên phía trình duyệt.
    await endSession();
    // replace: nút Back không đưa người dùng quay lại khu bệnh nhân.
    router.replace("/login");
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button
            aria-label={`Tài khoản: ${displayName}`}
            className="h-12 gap-2 px-2 text-base"
            variant="ghost"
          />
        }
      >
        <Avatar className="size-9">
          <AvatarFallback className="text-sm font-medium">
            {initialsOf(displayName)}
          </AvatarFallback>
        </Avatar>
        <span className="hidden max-w-40 truncate font-medium sm:inline">
          {displayName}
        </span>
        <ChevronDown aria-hidden />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-64">
        <div className="px-2 py-2">
          <p className="text-base font-medium">{displayName}</p>
          <p className="text-sm text-muted-foreground">{roleLabel}</p>
        </div>
        <DropdownMenuSeparator />
        {links.map((link) => (
          <DropdownMenuItem
            className="h-11 text-base"
            key={link.href}
            render={<Link href={link.href} />}
          >
            <link.icon aria-hidden />
            {link.label}
          </DropdownMenuItem>
        ))}
        <DropdownMenuItem className="h-11 text-base" onClick={signOut}>
          <LogOut aria-hidden />
          Đăng xuất
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
