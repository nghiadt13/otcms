import type { Metadata } from "next";
import { VisitOverview } from "@/features/doctor-exam/components/VisitOverview";

export const metadata: Metadata = { title: "Tổng quan lượt khám" };

export default function DoctorVisitWorkspacePage() {
  return <VisitOverview />;
}
