import type { Notification } from "../types/notification";

// LỚP API GIẢ (xem roomApi.ts): dữ liệu nằm trong bộ nhớ, mất khi tải lại trang.
// Khi có endpoint, giữ nguyên chữ ký các hàm và đổi phần thân sang apiRequest.

let notifications: Notification[] = [
  {
    id: "n-001",
    title: "Đánh giá mới chờ duyệt",
    body: "Có 3 đánh giá của bệnh nhân đang chờ duyệt hiển thị.",
    createdAt: "2026-10-16T09:42:00+07:00",
    read: false,
    href: "/admin/reviews",
  },
  {
    id: "n-002",
    title: "Hóa đơn chưa thanh toán",
    body: "5 hóa đơn còn nợ quá 7 ngày.",
    createdAt: "2026-10-16T08:15:00+07:00",
    read: false,
    href: "/admin/unpaid-invoices",
  },
  {
    id: "n-003",
    title: "Phòng MRI tạm ngưng",
    body: "Phòng MRI đã được chuyển sang trạng thái tạm ngưng.",
    createdAt: "2026-10-15T17:30:00+07:00",
    read: true,
    href: "/admin/rooms",
  },
];

const wait = (ms = 150) => new Promise((resolve) => setTimeout(resolve, ms));

export async function listNotifications(): Promise<Notification[]> {
  await wait();
  return notifications.map((item) => ({ ...item }));
}

export async function markNotificationRead(id: string): Promise<void> {
  await wait(60);
  notifications = notifications.map((item) =>
    item.id === id ? { ...item, read: true } : item,
  );
}

export async function markAllNotificationsRead(): Promise<void> {
  await wait(60);
  notifications = notifications.map((item) => ({ ...item, read: true }));
}
