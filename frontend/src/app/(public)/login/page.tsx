import type { Metadata } from "next";
import Link from "next/link";
import { AuthCard, authLinkClass } from "@/features/auth/components/AuthCard";
import { SignInForm } from "@/features/auth/components/SignInForm";

export const metadata: Metadata = { title: "Đăng nhập | OTCMS" };

// UC01 Đăng nhập: một màn chung cho bệnh nhân, nhân viên, quản trị viên và chủ
// phòng khám; vai trò quyết định chức năng mở ra sau khi đăng nhập (BR-01).
export default function LoginPage() {
  return (
    <AuthCard
      description="Dùng email và mật khẩu tài khoản phòng khám của bạn."
      footer={
        <>
          Bệnh nhân chưa có tài khoản?{" "}
          <Link className={authLinkClass} href="/register">
            Đăng ký
          </Link>
        </>
      }
      title="Đăng nhập"
    >
      <SignInForm />
    </AuthCard>
  );
}
