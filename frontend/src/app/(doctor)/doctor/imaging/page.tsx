import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { DOCTOR_ROUTES } from "@/lib/routes/doctorRoutes";

export const metadata: Metadata = { title: "Ảnh chẩn đoán" };

export default function DoctorImagingStudiesPage() {
  redirect(DOCTOR_ROUTES.todayPatients);
}
