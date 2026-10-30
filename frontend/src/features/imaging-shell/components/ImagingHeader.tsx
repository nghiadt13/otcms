"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Bell, LogOut, RefreshCw } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { useImaging } from "@/features/imaging/hooks/useImaging";
import { findActiveImagingItem } from "../imagingNavigation";

export function ImagingHeader({
  onReset,
  onExit,
}: {
  onReset: () => void;
  onExit: () => void;
}) {
  const pathname = usePathname();
  const { state } = useImaging();
  const unread = state.notifications.filter((n) => !n.read).length;
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
          24/10/2026 · 09:10
        </span>
        <Button
          variant="ghost"
          size="icon"
          aria-label="Khôi phục dữ liệu mẫu"
          onClick={onReset}
        >
          <RefreshCw />
        </Button>
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
