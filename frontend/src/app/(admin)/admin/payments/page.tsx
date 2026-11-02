import type { Metadata } from "next";
import { PaymentHistoryManagement } from "@/features/billing/components/PaymentHistoryManagement";

export const metadata: Metadata = { title: "Lịch sử thanh toán" };

export default function AdminPaymentsPage() {
  return <PaymentHistoryManagement />;
}
