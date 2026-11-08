import type { Metadata } from "next";
import Link from "next/link";
import { AuthAlert } from "@/features/auth/components/AuthAlert";
import { AuthCard, authLinkClass } from "@/features/auth/components/AuthCard";
import { SignInForm } from "@/features/auth/components/SignInForm";

export const metadata: Metadata = { title: "Đăng nhập | OTCMS" };

// UC01 Đăng nhập bệnh nhân. Nhân viên đăng nhập ở khu nội bộ (trang riêng, chưa có route).
// `next` là đích sau đăng nhập; SignInForm chỉ nhận đường dẫn trong khu bệnh nhân.
export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{
    next?: string | string[];
    registered?: string | string[];
  }>;
}) {
  const { next, registered } = await searchParams;
  const nextValue = Array.isArray(next) ? next[0] : next;
  // Vừa xác minh email xong ở /register (UC06): báo MSG-10 rồi để người dùng đăng nhập.
  const justRegistered =
    (Array.isArray(registered) ? registered[0] : registered) === "1";
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
      {justRegistered && (
        <AuthAlert className="mt-6" code="MSG-10" tone="success" />
      )}
      <SignInForm next={nextValue} />
    </AuthCard>
  );
}
