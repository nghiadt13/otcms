import type { Metadata } from "next";
import { DoctorDashboard } from "@/features/doctor-exam/components/DoctorDashboard";

export const metadata: Metadata = { title: "Bảng điều khiển" };

export default function DoctorHomePage() {
  return <DoctorDashboard />;
}
