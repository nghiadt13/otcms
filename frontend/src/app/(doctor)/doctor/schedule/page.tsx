import type { Metadata } from "next";
import { DoctorSchedule } from "@/features/appointments/components/DoctorSchedule";

export const metadata: Metadata = { title: "Lịch và phòng" };

export default function DoctorSchedulePage() {
  return <DoctorSchedule />;
}
