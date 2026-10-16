"use client";

import Link from "next/link";
import { Bell } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";
import { useNotifications } from "../hooks/useNotifications";

// Định dạng "16/10 09:42" theo giờ địa phương của trình duyệt.
const pad = (value: number) => String(value).padStart(2, "0");
const formatTime = (iso: string) => {
  const date = new Date(iso);
  return `${pad(date.getDate())}/${pad(date.getMonth() + 1)} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

export function NotificationBell() {
  const { notifications, unreadCount, markRead, markAllRead } =
    useNotifications();

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button
            variant="ghost"
            size="icon"
            className="relative"
            aria-label={
              unreadCount > 0
                ? `Thông báo, ${unreadCount} chưa đọc`
                : "Thông báo"
            }
          />
        }
      >
        <Bell />
        {unreadCount > 0 ? (
          <span className="absolute top-0.5 right-0.5 flex min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] leading-4 font-medium text-white">
            {unreadCount}
          </span>
        ) : null}
      </DropdownMenuTrigger>
      <DropdownMenuContent className="w-80" align="end">
        <div className="flex items-center justify-between px-1.5 py-1.5">
          <span className="text-sm font-medium">Thông báo</span>
          <Button
            variant="link"
            size="xs"
            disabled={unreadCount === 0}
            onClick={() => void markAllRead()}
          >
            Đánh dấu đã đọc
          </Button>
        </div>
        <DropdownMenuSeparator />
        {notifications.length === 0 ? (
          <p className="px-2 py-6 text-center text-sm text-muted-foreground">
            Chưa có thông báo nào.
          </p>
        ) : (
          notifications.map((item) => (
            <DropdownMenuItem
              key={item.id}
              className="items-start gap-2 py-2"
              render={<Link href={item.href ?? "/admin"} />}
              onClick={() => void markRead(item.id)}
            >
              <span
                aria-hidden
                className={cn(
                  "mt-1.5 size-2 shrink-0 rounded-full",
                  item.read ? "bg-transparent" : "bg-primary",
                )}
              />
              <span className="grid gap-0.5">
                <span className="text-sm font-medium">{item.title}</span>
                <span className="text-xs text-muted-foreground">
                  {item.body}
                </span>
                <time
                  dateTime={item.createdAt}
                  className="text-xs text-muted-foreground"
                >
                  {formatTime(item.createdAt)}
                </time>
              </span>
            </DropdownMenuItem>
          ))
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
