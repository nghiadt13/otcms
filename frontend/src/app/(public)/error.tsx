"use client";

import Link from "next/link";
import { useEffect } from "react";

// Lỗi runtime trong khu công khai. Bên trong root layout `(public)` nên dùng
// Tailwind thuần như các trang khác của khu này.
export default function PublicError({
  error,
  retry,
}: {
  error: Error & { digest?: string };
  retry: () => void;
}) {
  useEffect(() => {
    // Chỗ gắn dịch vụ ghi log lỗi khi có; tạm thời ghi ra console.
    console.error(error);
  }, [error]);

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 bg-canvas px-6 text-center text-slate-900">
      <h1 className="text-2xl font-semibold">Đã có lỗi xảy ra</h1>
      <p className="max-w-md text-sm text-slate-600">
        Không tải được trang này. Hãy thử lại hoặc quay về trang chủ.
      </p>
      <div className="flex gap-3">
        <button
          className="rounded-lg bg-brand px-4 py-2 text-sm font-semibold text-white hover:bg-brand-hover"
          onClick={() => retry()}
          type="button"
        >
          Thử lại
        </button>
        <Link
          className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold hover:bg-slate-100"
          href="/"
        >
          Trang chủ
        </Link>
      </div>
    </main>
  );
}
