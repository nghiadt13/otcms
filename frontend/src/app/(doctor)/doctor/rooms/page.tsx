import type { Metadata } from "next";
import { StaffRoomsScreen } from "@/components/rooms/StaffRoomsScreen";

export const metadata: Metadata = { title: "Danh sách phòng" };

export default function DoctorRoomsPage() {
  return <StaffRoomsScreen />;
}
