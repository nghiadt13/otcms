"use client";

import { ChevronRight } from "lucide-react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { PortalEmptyState } from "@/components/patient/PortalEmptyState";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import type { useNotificationList } from "../hooks/useNotificationList";
import type { NotificationItem } from "../types/notification";
import { notificationTimeLabel } from "../utils/notificationTimeLabel";

type PatientNotificationListProps = {
  list: ReturnType<typeof useNotificationList>;
  emptyText: string;
  onOpen: (item: NotificationItem) => void;
};

/** Danh sách thông báo khu bệnh nhân: list có vạch chia, cả dòng bấm được, có trang trước/sau. */
export function PatientNotificationList({
  list,
  emptyText,
  onOpen,
}: PatientNotificationListProps) {
  if (list.loading) return <NotificationListSkeleton />;
  if (list.error)
    return (
      <LoadError
        message={`Không tải được thông báo. ${list.error}`}
        onRetry={list.reload}
      />
    );

  const data = list.data;
  const items = data?.items ?? [];
  if (!data || items.length === 0)
    return <PortalEmptyState>{emptyText}</PortalEmptyState>;

  return (
    <div className="space-y-4">
      <ul className="divide-y overflow-hidden rounded-lg border bg-card">
        {items.map((item) => (
          <li key={item.id}>
            <button
              className="flex min-h-12 w-full items-start gap-3 px-4 py-4 text-left transition-colors hover:bg-muted/50 focus-visible:bg-muted/50 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none motion-reduce:transition-none"
              onClick={() => onOpen(item)}
              type="button"
            >
              <span className="grid min-w-0 flex-1 gap-1 text-base">
                <span className="flex flex-wrap items-center gap-2">
                  {!item.read ? (
                    <StatusBadge tone="info">Chưa đọc</StatusBadge>
                  ) : null}
                  <span
                    className={cn("break-words", !item.read && "font-semibold")}
                  >
                    {item.title}
                  </span>
                </span>
                <time
                  className="text-sm text-muted-foreground tabular-nums"
                  dateTime={item.createdAt}
                >
                  {notificationTimeLabel(item.createdAt)}
                </time>
              </span>
              <ChevronRight
                aria-hidden
                className="mt-1 size-5 shrink-0 text-muted-foreground"
              />
            </button>
          </li>
        ))}
      </ul>
      {data.totalPages > 1 ? (
        <Pager
          page={list.page}
          totalPages={data.totalPages}
          onPageChange={list.setPage}
        />
      ) : null}
    </div>
  );
}

function Pager({
  page,
  totalPages,
  onPageChange,
}: {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}) {
  return (
    <nav
      aria-label="Phân trang thông báo"
      className="grid grid-cols-2 items-center gap-3"
    >
      <Button
        className="h-12 text-base"
        disabled={page === 0}
        onClick={() => onPageChange(page - 1)}
        variant="outline"
      >
        Trang trước
      </Button>
      <Button
        className="h-12 text-base"
        disabled={page + 1 >= totalPages}
        onClick={() => onPageChange(page + 1)}
        variant="outline"
      >
        Trang sau
      </Button>
      <span className="col-span-2 text-center text-base tabular-nums">
        Trang {page + 1}/{totalPages}
      </span>
    </nav>
  );
}

/** Khung chờ đúng hình một dòng thông báo. */
export function NotificationListSkeleton() {
  return (
    <div
      aria-busy="true"
      className="divide-y overflow-hidden rounded-lg border bg-card"
      role="status"
    >
      <span className="sr-only">Đang tải thông báo</span>
      {[0, 1, 2].map((row) => (
        <div className="space-y-2 px-4 py-4" key={row}>
          <Skeleton className="h-5 w-3/4" />
          <Skeleton className="h-4 w-1/2" />
        </div>
      ))}
    </div>
  );
}

/** Lỗi tải có nút Thử lại; nói rõ chuyện gì đã xảy ra. */
export function LoadError({
  message,
  onRetry,
}: {
  message: string;
  onRetry: () => void;
}) {
  return (
    <div className="grid gap-4 rounded-lg border bg-card p-4" role="alert">
      <p className="text-base text-destructive">{message}</p>
      <Button
        className="h-12 w-full text-base sm:w-auto"
        onClick={onRetry}
        variant="outline"
      >
        Thử lại
      </Button>
    </div>
  );
}
