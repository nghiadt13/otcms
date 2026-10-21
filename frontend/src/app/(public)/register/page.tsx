import type { Metadata } from "next";
import { RegisterPatientFlow } from "@/features/auth/components/RegisterPatientFlow";

export const metadata: Metadata = {
  title: "Đăng ký tài khoản bệnh nhân | OTCMS",
};

// UC06 Đăng ký tài khoản bệnh nhân (3 bước, xem RegisterPatientFlow).
export default function RegisterPage() {
  return <RegisterPatientFlow />;
}
