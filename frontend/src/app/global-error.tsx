"use client";

import "./(public)/public.css";

// Lỗi xảy ra ở chính root layout (hiếm). File này thay thế root layout nên phải
// tự dựng <html>/<body>. Lỗi trong từng trang đã có `error.tsx` của từng khu bắt.
export default function GlobalError({
  error,
  retry,
}: {
  error: Error & { digest?: string };
  retry: () => void;
}) {
  return (
    <html lang="vi">
      <body>
        <main className="flex min-h-screen flex-col items-center justify-center gap-4 bg-slate-50 px-6 text-center text-slate-900">
          <h1 className="text-2xl font-semibold">Đã có lỗi xảy ra</h1>
          <p className="max-w-md text-sm text-slate-600">
            Ứng dụng gặp sự cố ngoài dự kiến. Hãy thử lại; nếu lỗi lặp lại, báo
            cho quản trị viên
            {error.digest ? ` kèm mã ${error.digest}` : ""}.
          </p>
          <button
            className="rounded-lg bg-teal-700 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-800"
            onClick={() => retry()}
            type="button"
          >
            Thử lại
          </button>
        </main>
      </body>
    </html>
  );
}
