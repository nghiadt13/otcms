import type { Metadata } from "next";
import { DoctorPlaceholderPage } from "@/features/doctor-shell/components/DoctorPlaceholderPage";
import { DOCTOR_ROUTES } from "@/lib/routes/doctorRoutes";

export const metadata: Metadata = { title: "Ảnh chẩn đoán" };

export default function DoctorImagingStudiesPage() {
  return <DoctorPlaceholderPage href={DOCTOR_ROUTES.imagingStudies} />;
}
