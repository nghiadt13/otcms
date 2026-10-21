import type { Metadata } from "next";
import Link from "next/link";
import { AuthCard, authLinkClass } from "@/features/auth/components/AuthCard";
import { SignInForm } from "@/features/auth/components/SignInForm";

export const metadata: Metadata = { title: "Đăng nhập nhân viên | OTCMS" };

/** Giữ URL đăng nhập Admin/nhân viên được dùng trước khi đồng bộ frontend. */
export default function StaffLoginPage() {
  return (
    <AuthCard
      description="Dùng email và mật khẩu tài khoản nhân viên hoặc quản trị viên."
      footer={
        <>
          <Link className={authLinkClass} href="/login">
            Đăng nhập tài khoản bệnh nhân
          </Link>
        </>
      }
      title="Đăng nhập nhân viên"
    >
      <SignInForm staff />
    </AuthCard>
  );
}
