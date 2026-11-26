import type { Metadata } from "next";
import { MultiRoomVisitProgressScreen } from "@/features/doctor-exam/components/MultiRoomVisitProgressScreen";

export const metadata: Metadata = { title: "Tiến trình nhiều phòng" };

export default function DoctorVisitProgressPage() {
  return <MultiRoomVisitProgressScreen />;
}
