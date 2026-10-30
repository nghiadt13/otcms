import type { Metadata } from "next";
import { AdminServicesLive } from "@/features/clinic-services/components/AdminServicesLive";

export const metadata: Metadata = { title: "Dịch vụ và giá" };

export default function AdminServicesPage() {
  return <AdminServicesLive />;
}
