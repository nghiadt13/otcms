import type { Metadata } from "next";
import { DoctorPlaceholderPage } from "@/features/doctor-shell/components/DoctorPlaceholderPage";
import { DOCTOR_ROUTES } from "@/lib/routes/doctorRoutes";

export const metadata: Metadata = { title: "Bệnh nhân hôm nay" };

export default function DoctorTodayPatientsPage() {
  return <DoctorPlaceholderPage href={DOCTOR_ROUTES.todayPatients} />;
}
