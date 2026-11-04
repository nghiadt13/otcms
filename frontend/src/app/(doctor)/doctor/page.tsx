import type { Metadata } from "next";
import { DoctorPlaceholderPage } from "@/features/doctor-shell/components/DoctorPlaceholderPage";
import { DOCTOR_ROUTES } from "@/lib/routes/doctorRoutes";

export const metadata: Metadata = { title: "Bảng điều khiển" };

export default function DoctorHomePage() {
  return <DoctorPlaceholderPage href={DOCTOR_ROUTES.dashboard} />;
}
