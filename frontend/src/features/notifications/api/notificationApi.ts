import { mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import type { PageQuery, PageResponse } from "@/lib/api/pagination";
import type {
  NotificationItem,
  NotificationListFilters,
  UnreadNotificationCount,
} from "../types/notification";
import {
  FOREIGN_NOTIFICATION_ID,
  MOCK_NOTIFICATIONS,
} from "./notificationMockData";

// LỚP API GIẢ: dữ liệu nằm trong bộ nhớ, mất khi tải lại trang. Khi có backend giữ
// nguyên chữ ký hàm, đổi thân sang apiRequest("/api/v1/me/notifications...").
// BE lọc theo phiên đăng nhập; FE không gửi id người dùng (BR-01).

const LATENCY_MS = process.env.NODE_ENV === "test" ? 0 : 300;

let store: NotificationItem[] = structuredClone(MOCK_NOTIFICATIONS);
let failList = false;

/** Chỉ dùng trong test và demo: đặt lại kho; `failList` làm các hàm tải trả 500 MSG-04. */
export function resetNotificationMockStore(
  items: NotificationItem[] = MOCK_NOTIFICATIONS,
  options: { failList?: boolean } = {},
) {
  store = structuredClone(items);
  failList = options.failList ?? false;
}

/** GET /api/v1/me/notifications?unread=&page=&size= (mới nhất trước) */
export async function listMyNotifications(
  query: PageQuery & NotificationListFilters = {},
  signal?: AbortSignal,
): Promise<PageResponse<NotificationItem>> {
  await mockDelay(LATENCY_MS, signal);
  if (failList) mockFail(500, "MSG-04");
  const rows = store
    .filter((item) => (query.unread ? !item.read : true))
    .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
  return structuredClone(paginate(rows, query));
}

/** GET /api/v1/me/notifications/unread-count */
export async function getUnreadNotificationCount(
  signal?: AbortSignal,
): Promise<UnreadNotificationCount> {
  await mockDelay(LATENCY_MS, signal);
  if (failList) mockFail(500, "MSG-04");
  return { unreadCount: store.filter((item) => !item.read).length };
}

/** POST /api/v1/me/notifications/{id}/read (gọi lại khi đã đọc vẫn 200) */
export async function markNotificationRead(
  id: string,
): Promise<NotificationItem> {
  await mockDelay(LATENCY_MS);
  const item = store.find((entry) => entry.id === id);
  if (id === FOREIGN_NOTIFICATION_ID || !item) mockFail(404, "MSG-50");
  item.read = true;
  item.allowedActions = [];
  return structuredClone(item);
}

/** POST /api/v1/me/notifications/read-all */
export async function markAllNotificationsRead(): Promise<{
  updatedCount: number;
}> {
  await mockDelay(LATENCY_MS);
  let updatedCount = 0;
  for (const item of store) {
    if (item.read) continue;
    item.read = true;
    item.allowedActions = [];
    updatedCount += 1;
  }
  return { updatedCount };
}
