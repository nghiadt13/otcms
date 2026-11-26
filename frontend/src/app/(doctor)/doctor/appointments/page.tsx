import type { Metadata } from "next";
import { DoctorAppointmentHistory } from "@/features/appointments/components/DoctorAppointmentHistory";

export const metadata: Metadata = { title: "Lịch sử lịch hẹn" };

export default function DoctorAppointmentsPage() {
  return <DoctorAppointmentHistory />;
}
