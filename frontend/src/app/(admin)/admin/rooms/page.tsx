import type { Metadata } from "next";
import { RoomManagement } from "@/features/rooms/components/RoomManagement";

export const metadata: Metadata = { title: "Phòng và quầy" };

export default function AdminRoomsPage() {
  return <RoomManagement />;
}
