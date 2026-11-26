"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Bell, LogOut } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { useUnreadNotificationCount } from "@/features/notifications/hooks/useUnreadNotificationCount";
import { findActiveImagingItem } from "../imagingNavigation";

export function ImagingHeader({ onExit }: { onExit: () => void }) {
  const pathname = usePathname();
  const unread = useUnreadNotificationCount();
  const currentItem = findActiveImagingItem(pathname);
  return (
    <header className="flex h-14 shrink-0 items-center gap-3 border-b px-4">
      <SidebarTrigger aria-label="Mở hoặc thu gọn menu" />
      <Link
        href={currentItem?.href ?? "/imaging"}
        className="truncate text-sm font-medium"
        aria-label={`Quay lại ${(currentItem?.title ?? "Tổng quan").toLowerCase()}`}
      >
        {currentItem?.title ?? "Chẩn đoán hình ảnh"}
      </Link>
      <div className="ml-auto flex items-center gap-2">
        <span className="hidden text-xs text-muted-foreground md:block">
          {new Intl.DateTimeFormat("vi-VN", { dateStyle: "medium" }).format(
            new Date(),
          )}
        </span>
        <Link
          href="/imaging/notifications"
          className={buttonVariants({ variant: "ghost", size: "icon" })}
          aria-label={`Thông báo, ${unread} chưa đọc`}
        >
          <Bell />
        </Link>
        <Button
          variant="ghost"
          size="icon"
          aria-label="Đăng xuất"
          onClick={onExit}
        >
          <LogOut />
        </Button>
      </div>
    </header>
  );
}
