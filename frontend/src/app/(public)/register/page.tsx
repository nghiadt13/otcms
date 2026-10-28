import type { Metadata } from "next";
import { RegisterPatientFlow } from "@/features/auth/components/RegisterPatientFlow";

export const metadata: Metadata = {
  title: "Đăng ký tài khoản bệnh nhân | OTCMS",
};

// UC06 Đăng ký tài khoản bệnh nhân (3 bước, xem RegisterPatientFlow).
// `next` là đích sau khi đăng nhập ở bước cuối (ví dụ quay lại màn đặt lịch); SignInForm lọc lại.
export default async function RegisterPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string | string[] }>;
}) {
  const { next } = await searchParams;
  return <RegisterPatientFlow next={Array.isArray(next) ? next[0] : next} />;
}
