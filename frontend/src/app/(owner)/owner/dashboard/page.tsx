import type { Metadata } from "next";
import { OwnerDashboard } from "@/features/dashboard/owner/components/OwnerDashboard";

export const metadata: Metadata = { title: "Tổng quan kinh doanh" };

export default function OwnerDashboardPage() {
  return <OwnerDashboard />;
}
