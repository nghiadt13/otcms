import type { Metadata } from "next";
import { ActivityLogManagement } from "@/features/activity-logs/components/ActivityLogManagement";

export const metadata: Metadata = { title: "Nhật ký hoạt động" };

export default function AdminActivityLogsPage() {
  return <ActivityLogManagement />;
}
