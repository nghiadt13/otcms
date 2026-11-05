import type { Metadata } from "next";
import { ClosingScreen } from "@/features/doctor-exam/components/ClosingScreen";

export const metadata: Metadata = { title: "Hoàn tất lượt khám" };

export default function DoctorClosingPage() {
  return <ClosingScreen />;
}
