import type { Metadata } from "next";
import { AuthCard } from "@/features/auth/components/AuthCard";
import { ResetPasswordForm } from "@/features/auth/components/ResetPasswordForm";

export const metadata: Metadata = { title: "Đặt mật khẩu mới | OTCMS" };

// UC02 Đặt lại mật khẩu, phần mở từ link trong email (`?token=...`).
export default async function ResetPasswordPage({
  searchParams,
}: {
  searchParams: Promise<{ token?: string | string[] }>;
}) {
  const { token } = await searchParams;
  return (
    <AuthCard
      description="Chọn mật khẩu mới cho tài khoản của bạn."
      title="Đặt mật khẩu mới"
    >
      <ResetPasswordForm token={typeof token === "string" ? token : ""} />
    </AuthCard>
  );
}
