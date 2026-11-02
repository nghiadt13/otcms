import type { Metadata } from "next";
import Link from "next/link";
import { AuthCard, authLinkClass } from "@/features/auth/components/AuthCard";
import { ForgotPasswordForm } from "@/features/auth/components/ForgotPasswordForm";

export const metadata: Metadata = { title: "Quên mật khẩu | OTCMS" };

// UC02 Đặt lại mật khẩu, phần yêu cầu khôi phục qua email.
export default function ForgotPasswordPage() {
  return (
    <AuthCard
      description="Nhập email tài khoản, chúng tôi sẽ gửi hướng dẫn đặt lại mật khẩu."
      footer={
        <>
          Nhớ mật khẩu rồi?{" "}
          <Link className={authLinkClass} href="/login">
            Đăng nhập
          </Link>
        </>
      }
      title="Quên mật khẩu"
    >
      <ForgotPasswordForm />
    </AuthCard>
  );
}
