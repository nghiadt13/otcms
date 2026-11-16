import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import { AuthCard, authLinkClass } from "@/features/auth/components/AuthCard";
import { RegisteredToast } from "@/features/auth/components/RegisteredToast";
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
  const nextValue = Array.isArray(next) ? next[0] : next;
  // Vừa xác minh email xong ở /register (UC06): RegisteredToast hiện toast MSG-10
  // khi URL có `?registered=1`.
  return (
    <AuthCard
      description="Dành cho bệnh nhân. Dùng email và mật khẩu bạn đã đăng ký."
      footer={
        <>
          Chưa có tài khoản?{" "}
          <Link
            className={authLinkClass}
            href={
              nextValue
                ? `/register?next=${encodeURIComponent(nextValue)}`
                : "/register"
            }
          >
            Đăng ký
          </Link>
        </>
      }
      title="Đăng nhập"
    >
      <Suspense fallback={null}>
        <RegisteredToast />
      </Suspense>
      <SignInForm next={nextValue} />
    </AuthCard>
  );
}
