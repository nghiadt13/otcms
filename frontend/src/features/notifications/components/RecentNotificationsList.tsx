"use client";

import { CheckCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { formatDateTime } from "@/lib/format/formatDate";
import { cn } from "@/lib/utils";
import { useMarkNotificationRead } from "../hooks/useMarkNotificationRead";
import type { useNotificationList } from "../hooks/useNotificationList";
import type { NotificationItem } from "../types/notification";

type RecentNotificationsListProps = {
  list: ReturnType<typeof useNotificationList>;
  onOpenItem: (item: NotificationItem) => void;
};

/** 5 thông báo mới nhất trong popover của chuông. */
export function RecentNotificationsList({
  list,
  onOpenItem,
}: RecentNotificationsListProps) {
  const { data, loading, error, reload, replaceItem } = list;
  const { pendingId, markRead } = useMarkNotificationRead();

  if (loading && !data) {
    return (
      <div className="grid gap-2" aria-hidden>
        {[0, 1, 2].map((row) => (
          <Skeleton key={row} className="h-10 w-full" />
        ))}
      </div>
    );
  }
  if (error) {
    return (
      <div role="alert" className="grid justify-items-start gap-2 text-sm">
        <p className="text-destructive">{error}</p>
        <Button variant="outline" size="sm" onClick={reload}>
          Thử lại
        </Button>
      </div>
    );
  }
  if (!data || data.items.length === 0) {
    return (
      <p className="py-4 text-center text-sm text-muted-foreground">
        Bạn chưa có thông báo nào.
      </p>
    );
  }

  async function handleMarkRead(item: NotificationItem) {
    const updated = await markRead(item.id);
    if (updated) replaceItem((entry) => entry.id === item.id, updated);
  }

  return (
    <ul className="grid gap-0.5">
      {data.items.map((item) => (
        <li key={item.id} className="flex items-center gap-1">
          <Button
            variant="ghost"
            className="h-auto min-w-0 flex-1 justify-start gap-2 py-1.5 text-left whitespace-normal"
            onClick={() => onOpenItem(item)}
          >
            <span
              aria-hidden
              className={cn(
                "size-2 shrink-0 rounded-full",
                item.read ? "bg-transparent" : "bg-primary",
              )}
            />
            <span className="grid min-w-0 flex-1">
              <span
                className={cn(
                  "truncate text-sm",
                  item.read ? "font-normal" : "font-medium",
                )}
              >
                {!item.read ? <span className="sr-only">Chưa đọc: </span> : null}
                {item.title}
              </span>
              <time
                dateTime={item.createdAt}
                className="text-xs text-muted-foreground tabular-nums"
              >
                {formatDateTime(item.createdAt)}
              </time>
            </span>
          </Button>
          {item.allowedActions.includes("markRead") ? (
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={`Đánh dấu đã đọc: ${item.title}`}
              disabled={pendingId === item.id}
              onClick={() => void handleMarkRead(item)}
            >
              <CheckCheck aria-hidden />
            </Button>
          ) : null}
        </li>
      ))}
    </ul>
  );
}
