"use client";

import { useState } from "react";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { markNotificationRead } from "../api/notificationApi";
import type { NotificationItem } from "../types/notification";
import { emitNotificationsChanged } from "./useUnreadNotificationCount";

/**
 * Đánh dấu một thông báo đã đọc, dùng chung cho chuông, hộp thư và Sheet.
 * Thành công trả bản ghi mới; lỗi (404 không còn, 5xx) báo toast và trả null,
 * thông báo vẫn được giữ nguyên trong danh sách.
 */
export function useMarkNotificationRead() {
  const [pendingId, setPendingId] = useState<string | null>(null);

  async function markRead(id: string): Promise<NotificationItem | null> {
    setPendingId(id);
    try {
      const updated = await markNotificationRead(id);
      notifySuccess("Đã đánh dấu thông báo là đã đọc.");
      emitNotificationsChanged();
      return updated;
    } catch (err) {
      notifyError(err);
      return null;
    } finally {
      setPendingId(null);
    }
  }

  return { pendingId, markRead };
}
