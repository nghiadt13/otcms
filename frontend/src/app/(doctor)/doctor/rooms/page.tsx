import type { Metadata } from "next";
import { DoctorPlaceholderPage } from "@/features/doctor-shell/components/DoctorPlaceholderPage";
import { DOCTOR_ROUTES } from "@/lib/routes/doctorRoutes";

export const metadata: Metadata = { title: "Danh sách phòng" };

export default function DoctorRoomsPage() {
  return <DoctorPlaceholderPage href={DOCTOR_ROUTES.rooms} />;
}
