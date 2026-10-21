// Thông báo cá nhân của người đang đăng nhập (UC63), tài nguyên /api/v1/me/notifications.
// Khác "Thông báo phòng khám" (feature announcements). Hình dữ liệu = JSON contract.

export type NotificationAction = "markRead";

// TẠM: danh sách loại bản ghi liên quan chưa chốt với nhóm.
export type NotificationRecordKind = "APPOINTMENT";

export interface NotificationRelatedRecord {
  kind: NotificationRecordKind;
  id: string;
  /** false: bản ghi đã mất hoặc ngoài quyền, ẩn nút "Mở" và báo không còn. */
  available: boolean;
}

export interface NotificationItem {
  id: string;
  title: string;
  /** Nội dung đầy đủ, hiện trong Sheet chi tiết. */
  content: string;
  /** ISO có offset, ví dụ "2026-10-20T08:45:00+07:00". */
  createdAt: string;
  read: boolean;
  relatedRecord: NotificationRelatedRecord | null;
  /** ["markRead"] khi chưa đọc, [] khi đã đọc. */
  allowedActions: NotificationAction[];
}

export interface NotificationListFilters {
  /** undefined = tất cả; true = chỉ chưa đọc. */
  unread?: boolean;
}

export interface UnreadNotificationCount {
  unreadCount: number;
}
