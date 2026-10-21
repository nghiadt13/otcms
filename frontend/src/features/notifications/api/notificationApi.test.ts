import { beforeEach, describe, expect, it } from "vitest";
import { isApiError } from "@/lib/api/apiError";
import {
  getUnreadNotificationCount,
  listMyNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  resetNotificationMockStore,
} from "./notificationApi";
import { MOCK_NOTIFICATIONS } from "./notificationMockData";

async function failure(promise: Promise<unknown>) {
  try {
    await promise;
  } catch (err) {
    if (isApiError(err)) return err;
  }
  throw new Error("Mong đợi ApiError");
}

beforeEach(() => resetNotificationMockStore());

describe("notificationApi", () => {
  it("23 thông báo chia 2 trang, mới nhất trước", async () => {
    const first = await listMyNotifications({ page: 0, size: 20 });
    const second = await listMyNotifications({ page: 1, size: 20 });
    expect(first).toMatchObject({ totalItems: 23, totalPages: 2 });
    expect(first.items).toHaveLength(20);
    expect(second.items).toHaveLength(3);
    const times = first.items.map((item) => Date.parse(item.createdAt));
    expect(times).toEqual([...times].sort((a, b) => b - a));
  });

  it("lọc unread chỉ trả thông báo chưa đọc, khớp số đếm", async () => {
    const page = await listMyNotifications({ unread: true, size: 100 });
    const { unreadCount } = await getUnreadNotificationCount();
    expect(page.items.every((item) => !item.read)).toBe(true);
    expect(page.totalItems).toBe(unreadCount);
    expect(unreadCount).toBeGreaterThan(0);
  });

  it("đánh dấu đã đọc: giảm số chưa đọc, bỏ markRead, idempotent", async () => {
    const target = MOCK_NOTIFICATIONS.find((item) => !item.read)!;
    const before = (await getUnreadNotificationCount()).unreadCount;
    const updated = await markNotificationRead(target.id);
    expect(updated).toMatchObject({ read: true, allowedActions: [] });
    expect((await getUnreadNotificationCount()).unreadCount).toBe(before - 1);
    await expect(markNotificationRead(target.id)).resolves.toMatchObject({
      read: true,
    });
    expect((await getUnreadNotificationCount()).unreadCount).toBe(before - 1);
  });

  it("n-foreign và id lạ trả 404 MSG-50", async () => {
    for (const id of ["n-foreign", "khong-co"]) {
      const err = await failure(markNotificationRead(id));
      expect([err.status, err.code]).toEqual([404, "MSG-50"]);
    }
  });

  it("đánh dấu tất cả đã đọc trả updatedCount", async () => {
    const unread = (await getUnreadNotificationCount()).unreadCount;
    await expect(markAllNotificationsRead()).resolves.toEqual({
      updatedCount: unread,
    });
    expect((await getUnreadNotificationCount()).unreadCount).toBe(0);
    await expect(markAllNotificationsRead()).resolves.toEqual({
      updatedCount: 0,
    });
  });

  it("kho rỗng trả danh sách rỗng; failList trả 500 MSG-04", async () => {
    resetNotificationMockStore([]);
    expect(await listMyNotifications()).toMatchObject({
      items: [],
      totalItems: 0,
    });
    resetNotificationMockStore(MOCK_NOTIFICATIONS, { failList: true });
    const err = await failure(listMyNotifications());
    expect([err.status, err.code]).toEqual([500, "MSG-04"]);
  });
});
