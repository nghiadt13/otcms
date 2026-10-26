import type { Metadata } from "next";
import Link from "next/link";
import { AuthCard, authLinkClass } from "@/features/auth/components/AuthCard";
import { SignInForm } from "@/features/auth/components/SignInForm";

export const metadata: Metadata = { title: "Đăng nhập | OTCMS" };

// UC01 Đăng nhập bệnh nhân. Nhân viên đăng nhập ở khu nội bộ (trang riêng, chưa có route).
// `next` là đích sau đăng nhập; SignInForm chỉ nhận đường dẫn trong khu bệnh nhân.
export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string | string[] }>;
}) {
  const { next } = await searchParams;
  return (
    <AuthCard
      description="Dành cho bệnh nhân. Dùng email và mật khẩu bạn đã đăng ký."
      footer={
        <>
          Chưa có tài khoản?{" "}
          <Link className={authLinkClass} href="/register">
            Đăng ký
          </Link>
        </>
      }
      title="Đăng nhập"
    >
      <SignInForm next={Array.isArray(next) ? next[0] : next} />
    </AuthCard>
  );
}
