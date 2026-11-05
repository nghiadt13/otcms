import type { Metadata } from "next";
import { ParaclinicalScreen } from "@/features/doctor-exam/components/ParaclinicalScreen";

export const metadata: Metadata = { title: "Cận lâm sàng" };

export default function DoctorParaclinicalPage() {
  return <ParaclinicalScreen />;
}
