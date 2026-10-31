"use client";

import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { formatDateTime } from "@/lib/format/formatDate";
import { cn } from "@/lib/utils";
import { useMarkNotificationRead } from "../hooks/useMarkNotificationRead";
import type { NotificationItem } from "../types/notification";
import {
  adminRecordHref,
  type NotificationRecordHrefResolver,
} from "../utils/notificationRecordLinks";
import { NotificationRecordLink } from "./NotificationRecordLink";

export type NotificationSheetSize = "default" | "portal";

type NotificationDetailSheetProps = {
  item: NotificationItem | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Gọi khi đánh dấu đã đọc thành công để danh sách cập nhật dòng. */
  onMarkedRead: (updated: NotificationItem) => void;
  /** "portal": Sheet từ dưới lên, chữ text-base, nút cao h-12. Mặc định: khu admin. */
  size?: NotificationSheetSize;
  /** Đường dẫn màn của bản ghi liên quan. Mặc định: màn admin (giữ hành vi cũ). */
  resolveRecordHref?: NotificationRecordHrefResolver;
};

/**
 * Chi tiết một thông báo. Sheet không tự đánh dấu đã đọc; màn khu bệnh nhân đánh dấu khi
 * mở dòng (xem PatientNotificationList).
 */
export function NotificationDetailSheet({
  item,
  open,
  onOpenChange,
  onMarkedRead,
  size = "default",
  resolveRecordHref = adminRecordHref,
}: NotificationDetailSheetProps) {
  const portal = size === "portal";
  const { pendingId, markRead } = useMarkNotificationRead();
  const record = item?.relatedRecord ?? null;
  // Bản ghi không còn khả dụng thì không có đường dẫn: ẩn nút Mở.
  const href = record?.available ? resolveRecordHref(record) : null;
  const canMarkRead = item?.allowedActions.includes("markRead") ?? false;
  const pending = item !== null && pendingId === item.id;
  const largeButton = portal ? "h-12 text-base" : undefined;

  async function handleMarkRead() {
    if (!item) return;
    const updated = await markRead(item.id);
    if (updated) onMarkedRead(updated);
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side={portal ? "bottom" : "right"}
        className={cn(
          portal
            ? "max-h-[85dvh] overflow-y-auto"
            : "w-full data-[side=right]:w-full data-[side=right]:sm:max-w-md",
        )}
      >
        {item ? (
          <>
            <SheetHeader>
              <SheetTitle className={cn("pr-8", portal && "text-lg")}>
                {item.title}
              </SheetTitle>
              <SheetDescription className="flex flex-wrap items-center gap-2">
                <StatusBadge tone={item.read ? "neutral" : "info"}>
                  {item.read ? "Đã đọc" : "Chưa đọc"}
                </StatusBadge>
                <time dateTime={item.createdAt} className="tabular-nums">
                  {formatDateTime(item.createdAt)}
                </time>
              </SheetDescription>
            </SheetHeader>
            <div className="grid flex-1 content-start gap-4 px-4">
              <p
                className={cn(
                  "leading-relaxed whitespace-pre-line",
                  portal ? "text-base" : "text-sm",
                )}
              >
                {item.content}
              </p>
              {record ? (
                <NotificationRecordLink
                  href={href}
                  kind={record.kind}
                  portal={portal}
                />
              ) : null}
            </div>
            <SheetFooter
              className={cn(portal ? "gap-3" : "sm:flex-row sm:justify-end")}
            >
              {canMarkRead ? (
                <Button
                  variant="outline"
                  className={largeButton}
                  disabled={pending}
                  onClick={() => void handleMarkRead()}
                >
                  {pending ? "Đang lưu..." : "Đánh dấu đã đọc"}
                </Button>
              ) : null}
              <Button
                variant="outline"
                className={largeButton}
                onClick={() => onOpenChange(false)}
              >
                Đóng
              </Button>
            </SheetFooter>
          </>
        ) : null}
      </SheetContent>
    </Sheet>
  );
}
