import type { Metadata } from "next";
import { OwnerFinance } from "@/features/dashboard/owner/components/OwnerFinance";

export const metadata: Metadata = { title: "Tài chính" };

export default function OwnerFinancePage() {
  return <OwnerFinance />;
}
