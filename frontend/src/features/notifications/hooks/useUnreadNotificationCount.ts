"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { getUnreadNotificationCount } from "../api/notificationApi";

const CHANGED_EVENT = "notifications:changed";

/** Báo số chưa đọc đã đổi (sau khi đánh dấu đã đọc) để chuông tải lại. */
export function emitNotificationsChanged() {
  window.dispatchEvent(new Event(CHANGED_EVENT));
}

/**
 * Số thông báo chưa đọc. Không polling: tải khi mount, khi đổi route và khi nhận
 * sự kiện `notifications:changed`. Lỗi tải giữ số cũ, không làm hỏng header.
 */
export function useUnreadNotificationCount() {
  const pathname = usePathname();
  const [unreadCount, setUnreadCount] = useState(0);
  const [token, setToken] = useState(0);

  useEffect(() => {
    const onChanged = () => setToken((n) => n + 1);
    window.addEventListener(CHANGED_EVENT, onChanged);
    return () => window.removeEventListener(CHANGED_EVENT, onChanged);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    getUnreadNotificationCount(controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setUnreadCount(result.unreadCount);
      })
      .catch(() => {
        // Chuông không được làm hỏng cả trang: giữ số cũ.
      });
    return () => controller.abort();
  }, [pathname, token]);

  return unreadCount;
}
