import type { Metadata } from "next";
import { UnpaidItemsManagement } from "@/features/billing/components/UnpaidItemsManagement";

export const metadata: Metadata = { title: "Khoản chưa thanh toán" };

export default function AdminUnpaidItemsPage() {
  return <UnpaidItemsManagement />;
}
