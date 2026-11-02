import type { Metadata } from "next";
import { ServiceCatalogManagement } from "@/features/clinic-services/components/ServiceCatalogManagement";

export const metadata: Metadata = { title: "Dịch vụ và giá" };

export default function AdminServicesPage() {
  return <ServiceCatalogManagement />;
}
