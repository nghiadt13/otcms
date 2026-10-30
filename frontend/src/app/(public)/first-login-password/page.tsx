import type { Metadata } from "next";
import { AuthCard } from "@/features/auth/components/AuthCard";
import { FirstLoginPasswordForm } from "@/features/auth/components/FirstLoginPasswordForm";

export const metadata: Metadata = {
  title: "Đổi mật khẩu lần đầu | OTCMS",
  robots: { index: false, follow: false },
  referrer: "no-referrer",
};
export default function FirstLoginPasswordPage() {
  return (
    <AuthCard
      title="Đổi mật khẩu lần đầu"
      description="Đặt mật khẩu riêng để hoàn tất lần đăng nhập đầu tiên."
    >
      <FirstLoginPasswordForm />
    </AuthCard>
  );
}
