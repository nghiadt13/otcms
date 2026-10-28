"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { markAllNotificationsRead } from "../api/notificationApi";
import {
  useMarkNotificationRead,
  MARKED_READ_TEXT,
} from "../hooks/useMarkNotificationRead";
import { useNotificationList } from "../hooks/useNotificationList";
import {
  emitNotificationsChanged,
  useUnreadNotificationCount,
} from "../hooks/useUnreadNotificationCount";
import type { NotificationItem } from "../types/notification";
import type { NotificationRecordHrefResolver } from "../utils/notificationRecordLinks";
import { NotificationDetailSheet } from "./NotificationDetailSheet";
import { PatientNotificationList } from "./PatientNotificationList";

const PAGE_SIZE = 10;

type View = "all" | "unread";

const EMPTY_TEXT: Record<View, string> = {
  all: "Bạn chưa có thông báo nào. Thông báo về lịch khám, kết quả và thanh toán sẽ hiện ở đây.",
  unread: "Bạn đã đọc hết thông báo.",
};

/**
 * Nội dung màn Thông báo khi tài khoản đã có hồ sơ: tab Tất cả/Chưa đọc, đánh dấu tất cả,
 * danh sách và chi tiết. Mở một dòng chưa đọc thì tự đánh dấu đã đọc, im lặng (UC63 bước 5).
 */
export function PatientNotificationsBody({
  resolveRecordHref,
}: {
  resolveRecordHref: NotificationRecordHrefResolver;
}) {
  const list = useNotificationList(PAGE_SIZE);
  const unreadCount = useUnreadNotificationCount();
  const { markRead } = useMarkNotificationRead();
  const [selected, setSelected] = useState<NotificationItem | null>(null);
  const [sheetOpen, setSheetOpen] = useState(false);
  const [markingAll, setMarkingAll] = useState(false);

  const view: View = list.filters.unread ? "unread" : "all";

  function applyUpdated(updated: NotificationItem) {
    setSelected((current) => (current?.id === updated.id ? updated : current));
    list.replaceItem((entry) => entry.id === updated.id, updated);
  }

  function openItem(item: NotificationItem) {
    setSelected(item);
    setSheetOpen(true);
    if (!item.allowedActions.includes("markRead")) return;
    void markRead(item.id, { silent: true }).then((updated) => {
      if (updated) applyUpdated(updated);
    });
  }

  function handleSheetOpenChange(open: boolean) {
    setSheetOpen(open);
    // Đang xem Chưa đọc: dòng vừa đọc rời danh sách khi đóng chi tiết.
    if (!open && list.filters.unread) list.reload();
  }

  async function handleMarkAll() {
    setMarkingAll(true);
    try {
      await markAllNotificationsRead();
      notifySuccess(MARKED_READ_TEXT);
      emitNotificationsChanged();
      list.reload();
    } catch (err) {
      notifyError(err);
    } finally {
      setMarkingAll(false);
    }
  }

  return (
    <div className="space-y-4">
      <Tabs
        onValueChange={(value) =>
          list.setFilters({ unread: value === "unread" ? true : undefined })
        }
        value={view}
      >
        <TabsList className="grid h-12! w-full grid-cols-2">
          <TabsTrigger className="text-base" value="all">
            Tất cả
          </TabsTrigger>
          <TabsTrigger className="text-base" value="unread">
            Chưa đọc
          </TabsTrigger>
        </TabsList>
        <TabsContent className="mt-4 space-y-4" value={view}>
          {unreadCount > 0 ? (
            <div className="flex justify-end">
              <Button
                className="h-12 w-full text-base sm:w-auto"
                disabled={markingAll}
                onClick={() => void handleMarkAll()}
                variant="outline"
              >
                {markingAll ? "Đang lưu..." : "Đánh dấu tất cả đã đọc"}
              </Button>
            </div>
          ) : null}
          <PatientNotificationList
            emptyText={EMPTY_TEXT[view]}
            list={list}
            onOpen={openItem}
          />
        </TabsContent>
      </Tabs>
      <NotificationDetailSheet
        item={selected}
        onMarkedRead={applyUpdated}
        onOpenChange={handleSheetOpenChange}
        open={sheetOpen}
        resolveRecordHref={resolveRecordHref}
        size="portal"
      />
    </div>
  );
}
