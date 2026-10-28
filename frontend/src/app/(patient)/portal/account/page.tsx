import type { Metadata } from "next";
import { PatientAccountProfile } from "@/features/my-account/components/PatientAccountProfile";

export const metadata: Metadata = { title: "Tài khoản | Cổng bệnh nhân" };

// Tài khoản bệnh nhân (UC03, UC04): tên hiển thị, email, ảnh đại diện, mật khẩu.
export default function PatientAccountPage() {
  return <PatientAccountProfile />;
}
