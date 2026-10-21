"use client";

import Link from "next/link";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button, buttonVariants } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { formatDateTime } from "@/lib/format/formatDate";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { ADMIN_ROUTES } from "@/lib/routes/adminRoutes";
import { cn } from "@/lib/utils";
import { useMarkNotificationRead } from "../hooks/useMarkNotificationRead";
import type {
  NotificationItem,
  NotificationRecordKind,
} from "../types/notification";

// Màn đích của bản ghi liên quan. Phase 05 chưa có trang chi tiết lịch hẹn nên mở danh sách.
const RECORD_ROUTES: Record<NotificationRecordKind, string> = {
  APPOINTMENT: ADMIN_ROUTES.appointments,
};
const RECORD_LABELS: Record<NotificationRecordKind, string> = {
  APPOINTMENT: "Lịch hẹn",
};

type NotificationDetailSheetProps = {
  item: NotificationItem | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Gọi khi đánh dấu đã đọc thành công để danh sách cập nhật dòng. */
  onMarkedRead: (updated: NotificationItem) => void;
};

/** Chi tiết một thông báo. Mở xem không tự đánh dấu đã đọc. */
export function NotificationDetailSheet({
  item,
  open,
  onOpenChange,
  onMarkedRead,
}: NotificationDetailSheetProps) {
  const { pendingId, markRead } = useMarkNotificationRead();
  const record = item?.relatedRecord ?? null;
  const canMarkRead = item?.allowedActions.includes("markRead") ?? false;
  const pending = item !== null && pendingId === item.id;

  async function handleMarkRead() {
    if (!item) return;
    const updated = await markRead(item.id);
    if (updated) onMarkedRead(updated);
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full data-[side=right]:w-full data-[side=right]:sm:max-w-md">
        {item ? (
          <>
            <SheetHeader>
              <SheetTitle className="pr-8">{item.title}</SheetTitle>
              <SheetDescription className="flex items-center gap-2">
                <StatusBadge tone={item.read ? "neutral" : "info"}>
                  {item.read ? "Đã đọc" : "Chưa đọc"}
                </StatusBadge>
                <time dateTime={item.createdAt} className="tabular-nums">
                  {formatDateTime(item.createdAt)}
                </time>
              </SheetDescription>
            </SheetHeader>
            <div className="grid flex-1 content-start gap-4 px-4">
              <p className="text-sm leading-relaxed whitespace-pre-line">
                {item.content}
              </p>
              {record ? (
                <div className="flex flex-wrap items-center gap-2 text-sm">
                  <span className="text-muted-foreground">
                    Bản ghi liên quan: {RECORD_LABELS[record.kind]}
                  </span>
                  {record.available ? (
                    <Link
                      href={RECORD_ROUTES[record.kind]}
                      className={cn(
                        buttonVariants({ variant: "link", size: "sm" }),
                      )}
                    >
                      Mở
                    </Link>
                  ) : (
                    <span role="status" className="text-destructive">
                      {formatAppMessage("MSG-50")}
                    </span>
                  )}
                </div>
              ) : null}
            </div>
            <SheetFooter className="sm:flex-row sm:justify-end">
              {canMarkRead ? (
                <Button
                  variant="outline"
                  disabled={pending}
                  onClick={() => void handleMarkRead()}
                >
                  {pending ? "Đang lưu..." : "Đánh dấu đã đọc"}
                </Button>
              ) : null}
              <Button variant="outline" onClick={() => onOpenChange(false)}>
                Đóng
              </Button>
            </SheetFooter>
          </>
        ) : null}
      </SheetContent>
    </Sheet>
  );
}
