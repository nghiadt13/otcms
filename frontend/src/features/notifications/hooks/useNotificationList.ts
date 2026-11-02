"use client";

import { usePagedList } from "@/hooks/usePagedList";
import { listMyNotifications } from "../api/notificationApi";
import type {
  NotificationItem,
  NotificationListFilters,
} from "../types/notification";

/** Danh sách thông báo của tôi, phân trang. Hộp thư dùng size mặc định, chuông dùng 5. */
export function useNotificationList(pageSize?: number) {
  return usePagedList<NotificationItem, NotificationListFilters>(
    listMyNotifications,
    {},
    pageSize,
  );
}
