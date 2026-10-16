"use client";

import { useEffect, useState } from "react";
import {
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from "../api/notificationApi";
import type { Notification } from "../types/notification";

export function useNotifications() {
  const [notifications, setNotifications] = useState<Notification[]>([]);

  useEffect(() => {
    let active = true;
    listNotifications()
      .then((items) => {
        if (active) setNotifications(items);
      })
      .catch(() => {
        // Chuông thông báo không được làm hỏng cả trang: lỗi tải thì để danh sách rỗng.
      });
    return () => {
      active = false;
    };
  }, []);

  return {
    notifications,
    unreadCount: notifications.filter((item) => !item.read).length,
    async markRead(id: string) {
      setNotifications((current) =>
        current.map((item) =>
          item.id === id ? { ...item, read: true } : item,
        ),
      );
      await markNotificationRead(id);
    },
    async markAllRead() {
      setNotifications((current) =>
        current.map((item) => ({ ...item, read: true })),
      );
      await markAllNotificationsRead();
    },
  };
}
