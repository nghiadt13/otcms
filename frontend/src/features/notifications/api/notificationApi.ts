import { apiRequest } from "@/lib/api/client";
import { toQueryString, type PageQuery, type PageResponse } from "@/lib/api/pagination";
import type { NotificationItem, NotificationListFilters, UnreadNotificationCount } from "../types/notification";
const root = "/api/v1/me/notifications";
export function listMyNotifications(query: PageQuery & NotificationListFilters = {}, signal?: AbortSignal): Promise<PageResponse<NotificationItem>> { return apiRequest(`${root}${toQueryString({ ...query })}`, { signal }); }
export function getUnreadNotificationCount(signal?: AbortSignal): Promise<UnreadNotificationCount> { return apiRequest(`${root}/unread-count`, { signal }); }
export function markNotificationRead(id: string): Promise<NotificationItem> { return apiRequest(`${root}/${encodeURIComponent(id)}/read`, { method: "POST" }); }
export function markAllNotificationsRead(): Promise<{ updatedCount: number }> { return apiRequest(`${root}/read-all`, { method: "POST" }); }
