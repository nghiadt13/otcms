import type { Metadata } from "next";
import { TodayPatientsScreen } from "@/features/doctor-exam/components/TodayPatientsScreen";

export const metadata: Metadata = { title: "Bệnh nhân hôm nay" };

export default function DoctorTodayPatientsPage() {
  return <TodayPatientsScreen />;
}
