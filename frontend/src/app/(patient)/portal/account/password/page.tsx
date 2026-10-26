import type { Metadata } from "next";
import { PatientChangePasswordPage } from "@/features/my-account/components/PatientChangePasswordPage";

export const metadata: Metadata = { title: "Đổi mật khẩu | Cổng bệnh nhân" };

// Đổi mật khẩu của bệnh nhân (UC05).
export default function PatientChangePasswordRoute() {
  return <PatientChangePasswordPage />;
}
