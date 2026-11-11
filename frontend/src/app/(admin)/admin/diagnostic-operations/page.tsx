import type { Metadata } from "next";
import { DiagnosticOpsAdminManagement } from "@/features/billing/components/DiagnosticOpsAdminManagement";

export const metadata: Metadata = { title: "Sự cố phòng chụp và hoàn tiền" };

export default function DiagnosticOperationsPage() {
  return <DiagnosticOpsAdminManagement />;
}
