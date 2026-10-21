import type { Metadata } from "next";
import { AnnouncementManagement } from "@/features/announcements/components/AnnouncementManagement";

export const metadata: Metadata = { title: "Thông báo phòng khám" };

export default function AdminAnnouncementsPage() {
  return <AnnouncementManagement />;
}
