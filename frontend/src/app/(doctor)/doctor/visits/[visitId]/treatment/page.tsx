import type { Metadata } from "next";
import { TreatmentScreen } from "@/features/doctor-exam/components/TreatmentScreen";

export const metadata: Metadata = { title: "Điều trị và đơn thuốc" };

export default function DoctorTreatmentPage() {
  return <TreatmentScreen />;
}
