"use client";

import { useState } from "react";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { markNotificationRead } from "../api/notificationApi";
import type { NotificationItem } from "../types/notification";
import { emitNotificationsChanged } from "./useUnreadNotificationCount";

/** Câu báo khi đánh dấu đã đọc thành công (MSG-60 trong R3). */
export const MARKED_READ_TEXT = "Đã đánh dấu thông báo là đã đọc.";

export type MarkReadOptions = {
  /**
   * Không báo toast khi thành công. Khu bệnh nhân dùng khi mở chi tiết (chỉ đổi kiểu chữ);
   * lỗi vẫn báo.
   */
  silent?: boolean;
};

/**
 * Đánh dấu một thông báo đã đọc, dùng chung cho chuông, hộp thư và Sheet.
 * Thành công trả bản ghi mới; lỗi (404 không còn, 5xx) báo toast và trả null,
 * thông báo vẫn được giữ nguyên trong danh sách.
 */
export function useMarkNotificationRead() {
  const [pendingId, setPendingId] = useState<string | null>(null);

  async function markRead(
    id: string,
    options: MarkReadOptions = {},
  ): Promise<NotificationItem | null> {
    setPendingId(id);
    try {
      const updated = await markNotificationRead(id);
      if (!options.silent) notifySuccess(MARKED_READ_TEXT);
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
