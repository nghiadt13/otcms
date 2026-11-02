import type { Metadata } from "next";
import { WebsiteContentManagement } from "@/features/clinic-website/components/WebsiteContentManagement";

export const metadata: Metadata = { title: "Nội dung website" };

export default function AdminWebsiteContentPage() {
  return <WebsiteContentManagement />;
}
