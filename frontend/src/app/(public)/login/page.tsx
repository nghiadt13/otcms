import Link from "next/link";
import { SignInForm } from "@/features/auth/components/SignInForm";

export default function LoginPage() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-50 px-6 py-12">
      <div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <Link className="text-sm font-semibold text-teal-700" href="/">
          ← OTCMS
        </Link>
        <h1 className="mt-8 text-2xl font-semibold text-slate-900">
          Đăng nhập
        </h1>
        <p className="mt-2 text-sm text-slate-600">
          Sử dụng tài khoản Supabase Auth của phòng khám.
        </p>
        <SignInForm />
      </div>
    </main>
  );
}
