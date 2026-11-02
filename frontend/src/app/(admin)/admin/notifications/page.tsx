import type { Metadata } from "next";
import { NotificationListManagement } from "@/features/notifications/components/NotificationListManagement";

export const metadata: Metadata = { title: "Hộp thư thông báo" };

export default function AdminNotificationsPage() {
  return <NotificationListManagement />;
}
