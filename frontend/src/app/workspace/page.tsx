"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { getSupabaseBrowserClient } from "@/lib/supabase/browser";

export default function WorkspacePage() {
  const router = useRouter();
  const [email, setEmail] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    getSupabaseBrowserClient()
      .auth.getSession()
      .then(({ data }) => {
        if (!active) return;
        if (!data.session) router.replace("/login");
        else setEmail(data.session.user.email ?? "Tài khoản phòng khám");
      })
      .catch(() => router.replace("/login"));
    return () => {
      active = false;
    };
  }, [router]);

  async function signOut() {
    await getSupabaseBrowserClient().auth.signOut();
    router.replace("/login");
  }

  return (
    <main className="min-h-screen bg-slate-50 px-6 py-12 text-slate-900">
      <div className="mx-auto max-w-3xl">
        <div className="flex items-center justify-between gap-4">
          <Link className="font-semibold text-teal-700" href="/">
            OTCMS
          </Link>
          <button
            className="text-sm text-slate-600 underline"
            onClick={signOut}
            type="button"
          >
            Đăng xuất
          </button>
        </div>
        <h1 className="mt-16 text-3xl font-semibold">Không gian làm việc</h1>
        <p className="mt-4 text-slate-600">
          {email
            ? `Đã đăng nhập: ${email}`
            : "Đang kiểm tra phiên đăng nhập..."}
        </p>
        <p className="mt-6 rounded-xl border border-amber-200 bg-amber-50 p-5 text-amber-900">
          Đây là màn hình khởi đầu. Các màn hình nghiệp vụ và kiểm tra vai trò
          theo bản ghi sẽ được bổ sung cùng từng use case.
        </p>
      </div>
    </main>
  );
}
