import type { Metadata } from "next";
import Link from "next/link";
import { AuthCard, authLinkClass } from "@/features/auth/components/AuthCard";
import { SignInForm } from "@/features/auth/components/SignInForm";

export const metadata: Metadata = { title: "Đăng nhập nhân viên | OTCMS" };

// Lối đăng nhập nội bộ cho quản trị viên, chủ phòng khám và nhân viên. Không đăng ký, không
// quên mật khẩu tự phục vụ; sau khi vào, /workspace chuyển tới khu đúng vai trò.
export default function StaffLoginPage() {
  return (
    <AuthCard
      description="Dùng tên đăng nhập để vào khu làm việc. Tài khoản nhân viên cũ vẫn có thể dùng email."
      footer={
        <Link className={authLinkClass} href="/login">
          Đăng nhập tài khoản bệnh nhân
        </Link>
      }
      title="Đăng nhập nhân viên"
    >
      <SignInForm staff />
    </AuthCard>
  );
}
