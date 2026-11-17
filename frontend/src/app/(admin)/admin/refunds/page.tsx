import type { Metadata } from "next";
import { RefundsManagement } from "@/features/billing/components/RefundsManagement";

export const metadata: Metadata = { title: "Chờ hoàn tiền" };

export default function AdminRefundsPage() {
  return <RefundsManagement />;
}
