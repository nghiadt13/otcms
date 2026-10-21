"use client";

import { useState } from "react";
import { FilterBar } from "@/components/admin/FilterBar";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { TableFrame } from "@/components/admin/TableFrame";
import { TablePagination } from "@/components/admin/TablePagination";
import { TableStateRows } from "@/components/admin/TableStateRows";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableHead,
  TableHeader,
  TableRow,
  TableCell,
} from "@/components/ui/table";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { formatDateTime } from "@/lib/format/formatDate";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { cn } from "@/lib/utils";
import { markAllNotificationsRead } from "../api/notificationApi";
import { useNotificationList } from "../hooks/useNotificationList";
import {
  emitNotificationsChanged,
  useUnreadNotificationCount,
} from "../hooks/useUnreadNotificationCount";
import type { NotificationItem } from "../types/notification";
import { NotificationDetailSheet } from "./NotificationDetailSheet";

type ViewFilter = "all" | "unread";

/** Trang /admin/notifications: hộp thư thông báo cá nhân của Admin (UC63). */
export function NotificationListManagement() {
  const list = useNotificationList();
  const { data, loading, error, filters, setFilters, page, setPage, reload, replaceItem } =
    list;
  const unreadCount = useUnreadNotificationCount();
  const [selected, setSelected] = useState<NotificationItem | null>(null);
  const [sheetOpen, setSheetOpen] = useState(false);
  const [markingAll, setMarkingAll] = useState(false);

  const view: ViewFilter = filters.unread ? "unread" : "all";
  const items = data?.items ?? [];

  function openItem(item: NotificationItem) {
    setSelected(item);
    setSheetOpen(true);
  }

  function handleSheetOpenChange(open: boolean) {
    setSheetOpen(open);
    // Đang lọc Chưa đọc: dòng vừa đọc rời danh sách khi đóng Sheet.
    if (!open && filters.unread) reload();
  }

  function handleMarkedRead(updated: NotificationItem) {
    setSelected(updated);
    replaceItem((entry) => entry.id === updated.id, updated);
  }

  async function handleMarkAll() {
    setMarkingAll(true);
    try {
      await markAllNotificationsRead();
      notifySuccess("Đã đánh dấu tất cả thông báo là đã đọc.");
      emitNotificationsChanged();
      reload();
    } catch (err) {
      notifyError(err);
    } finally {
      setMarkingAll(false);
    }
  }

  const emptyText =
    view === "unread"
      ? "Bạn đã đọc hết thông báo."
      : "Bạn chưa có thông báo nào.";

  return (
    <div>
      <PageHeader
        title="Hộp thư thông báo"
        description="Thông báo do hệ thống gửi cho bạn."
        actions={
          unreadCount > 0 ? (
            <Button
              variant="outline"
              disabled={markingAll}
              onClick={() => void handleMarkAll()}
            >
              {markingAll ? "Đang lưu..." : "Đánh dấu tất cả đã đọc"}
            </Button>
          ) : null
        }
      />
      <FilterBar>
        <ToggleGroup
          variant="outline"
          spacing={0}
          aria-label="Lọc thông báo"
          value={[view]}
          onValueChange={(next) => {
            const value = next[0] as ViewFilter | undefined;
            if (value) setFilters({ unread: value === "unread" ? true : undefined });
          }}
        >
          <ToggleGroupItem value="all">Tất cả</ToggleGroupItem>
          <ToggleGroupItem value="unread">Chưa đọc</ToggleGroupItem>
        </ToggleGroup>
      </FilterBar>
      <TableFrame>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Thông báo</TableHead>
              <TableHead className="w-44 text-right">Thời gian</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableStateRows
              colSpan={2}
              loading={loading}
              error={error}
              empty={items.length === 0}
              onRetry={reload}
              emptyText={emptyText}
            />
            {!loading && !error
              ? items.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="whitespace-normal">
                      <div className="flex flex-wrap items-center gap-2">
                        {!item.read ? (
                          <StatusBadge tone="info">Chưa đọc</StatusBadge>
                        ) : null}
                        <Button
                          variant="link"
                          className={cn(
                            "h-auto p-0 text-left whitespace-normal text-foreground",
                            !item.read && "font-semibold",
                          )}
                          onClick={() => openItem(item)}
                        >
                          {item.title}
                        </Button>
                      </div>
                    </TableCell>
                    <TableCell className="text-right whitespace-nowrap tabular-nums">
                      <time dateTime={item.createdAt}>
                        {formatDateTime(item.createdAt)}
                      </time>
                    </TableCell>
                  </TableRow>
                ))
              : null}
          </TableBody>
        </Table>
      </TableFrame>
      {data && !error ? (
        <TablePagination
          page={page}
          totalPages={data.totalPages}
          totalItems={data.totalItems}
          onPageChange={setPage}
        />
      ) : null}
      <NotificationDetailSheet
        item={selected}
        open={sheetOpen}
        onOpenChange={handleSheetOpenChange}
        onMarkedRead={handleMarkedRead}
      />
    </div>
  );
}
