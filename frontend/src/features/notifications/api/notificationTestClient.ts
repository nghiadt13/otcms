import * as server from "./notificationTestServer";
export async function apiRequest(path: string, init: RequestInit = {}) {
  const url = new URL(path, "http://test");
  const root = "/api/v1/me/notifications";
  if (url.pathname === root)
    return server.listMyNotifications(
      {
        unread: url.searchParams.get("unread") === "true",
        page: Number(url.searchParams.get("page") || 0),
        size: Number(url.searchParams.get("size") || 20),
      },
      init.signal ?? undefined,
    );
  if (url.pathname === root + "/unread-count")
    return server.getUnreadNotificationCount(init.signal ?? undefined);
  if (url.pathname === root + "/read-all")
    return server.markAllNotificationsRead();
  const match = url.pathname.match(/\/notifications\/(.+)\/read$/);
  if (match) return server.markNotificationRead(decodeURIComponent(match[1]));
  throw new Error("Unexpected request: " + path);
}
