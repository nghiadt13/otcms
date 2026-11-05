import type { Metadata } from "next";
import { RecordsScreen } from "@/features/doctor-exam/components/RecordsScreen";

export const metadata: Metadata = { title: "Hồ sơ bệnh nhân" };

export default function DoctorRecordsPage() {
  return <RecordsScreen />;
}
