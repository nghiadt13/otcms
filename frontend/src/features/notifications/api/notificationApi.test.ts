import { expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { listMyNotifications, getUnreadNotificationCount, markNotificationRead, markAllNotificationsRead } from "./notificationApi";
vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
it("uses the current account inbox and read endpoints", async () => {
  const request = vi.mocked(apiRequest).mockResolvedValue({});
  await listMyNotifications({ unread: true, page: 1 }); await getUnreadNotificationCount(); await markNotificationRead("id"); await markAllNotificationsRead();
  expect(request).toHaveBeenNthCalledWith(1, "/api/v1/me/notifications?unread=true&page=1", { signal: undefined });
  expect(request).toHaveBeenNthCalledWith(2, "/api/v1/me/notifications/unread-count", { signal: undefined });
  expect(request).toHaveBeenNthCalledWith(3, "/api/v1/me/notifications/id/read", { method: "POST" });
  expect(request).toHaveBeenNthCalledWith(4, "/api/v1/me/notifications/read-all", { method: "POST" });
});
