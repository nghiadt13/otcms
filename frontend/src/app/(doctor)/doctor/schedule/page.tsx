import type { Metadata } from "next";
import { DoctorPlaceholderPage } from "@/features/doctor-shell/components/DoctorPlaceholderPage";
import { DOCTOR_ROUTES } from "@/lib/routes/doctorRoutes";

export const metadata: Metadata = { title: "Lịch và phòng" };

export default function DoctorSchedulePage() {
  return <DoctorPlaceholderPage href={DOCTOR_ROUTES.schedule} />;
}
