import type { Metadata } from "next";
import { ExaminationScreen } from "@/features/doctor-exam/components/ExaminationScreen";

export const metadata: Metadata = { title: "Khám bệnh" };

export default function DoctorExaminationPage() {
  return <ExaminationScreen />;
}
