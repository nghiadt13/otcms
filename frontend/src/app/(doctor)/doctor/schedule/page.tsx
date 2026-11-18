import type { Metadata } from "next";
import { StaffRoomsScreen } from "@/components/rooms/StaffRoomsScreen";
import { DoctorSchedule } from "@/features/appointments/components/DoctorSchedule";

export const metadata: Metadata = { title: "Lịch và phòng" };

export default function DoctorSchedulePage() {
  return (
    <div className="space-y-8">
      <DoctorSchedule />
      <StaffRoomsScreen embedded />
    </div>
  );
}
