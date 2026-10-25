"use client";

import Link from "next/link";
import { useState } from "react";
import { Bell } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTitle,
  PopoverTrigger,
} from "@/components/ui/popover";
import { ADMIN_ROUTES } from "@/lib/routes/adminRoutes";
import { cn } from "@/lib/utils";
import { useNotificationList } from "../hooks/useNotificationList";
import { useUnreadNotificationCount } from "../hooks/useUnreadNotificationCount";
import type { NotificationItem } from "../types/notification";
import { NotificationDetailSheet } from "./NotificationDetailSheet";
import { RecentNotificationsList } from "./RecentNotificationsList";

const RECENT_COUNT = 5;

// Chỉ mount khi popover mở nên danh sách tải lúc mở chuông.
function RecentPanel({
  onOpenItem,
}: {
  onOpenItem: (item: NotificationItem) => void;
}) {
  const list = useNotificationList(RECENT_COUNT);
  return <RecentNotificationsList list={list} onOpenItem={onOpenItem} />;
}

/** Chuông ở header: badge số chưa đọc, popover 5 thông báo mới nhất, Sheet chi tiết. */
export function NotificationBell() {
  const unreadCount = useUnreadNotificationCount();
  const [popoverOpen, setPopoverOpen] = useState(false);
  const [selected, setSelected] = useState<NotificationItem | null>(null);
  const [sheetOpen, setSheetOpen] = useState(false);

  function openItem(item: NotificationItem) {
    setSelected(item);
    setPopoverOpen(false);
    setSheetOpen(true);
  }

  return (
    <>
      <Popover open={popoverOpen} onOpenChange={setPopoverOpen}>
        <PopoverTrigger
          render={
            <Button
              variant="ghost"
              size="icon"
              className="relative"
              aria-label={
                unreadCount > 0
                  ? `Thông báo của bạn, ${unreadCount} chưa đọc`
                  : "Thông báo của bạn"
              }
            />
          }
        >
          <Bell aria-hidden />
          {unreadCount > 0 ? (
            // Số đã nằm trong aria-label nên ẩn khỏi trình đọc màn hình.
            <span
              aria-hidden
              className="absolute -top-1 -right-1 flex min-w-4 items-center justify-center rounded-full bg-coral px-1 text-[10px] leading-4 font-semibold text-coral-ink tabular-nums ring-2 ring-background"
            >
              {unreadCount > 9 ? "9+" : unreadCount}
            </span>
          ) : null}
        </PopoverTrigger>
        <PopoverContent
          align="end"
          className="w-[min(22rem,calc(100vw-2rem))]"
        >
          <div className="flex items-center justify-between gap-2">
            <PopoverTitle>Thông báo của bạn</PopoverTitle>
            <Link
              href={ADMIN_ROUTES.notifications}
              onClick={() => setPopoverOpen(false)}
              className={cn(buttonVariants({ variant: "link", size: "sm" }))}
            >
              Xem tất cả
            </Link>
          </div>
          <RecentPanel onOpenItem={openItem} />
        </PopoverContent>
      </Popover>
      <NotificationDetailSheet
        item={selected}
        open={sheetOpen}
        onOpenChange={setSheetOpen}
        onMarkedRead={setSelected}
      />
    </>
  );
}
