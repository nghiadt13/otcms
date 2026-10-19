import type { Metadata } from "next";
import Link from "next/link";
import "@/styles/app-theme.css";
import { appFont } from "@/lib/fonts";

// 404 cho URL không khớp route nào. File này bỏ qua mọi layout nên tự dựng
// <html>/<body> và tự nạp CSS; chỉ dùng Tailwind thuần để nhẹ và độc lập với
// theme của từng khu. 404 do `notFound()` trong một khu dùng `not-found.tsx` của khu đó.
export const metadata: Metadata = {
  title: "Không tìm thấy trang | OTCMS",
};

export default function GlobalNotFound() {
  return (
    <html lang="vi" className={appFont.variable}>
      <body>
        <main className="flex min-h-screen flex-col items-center justify-center gap-4 bg-canvas px-6 text-center text-slate-900">
          <p className="text-sm font-semibold tracking-[0.2em] text-brand">
            404
          </p>
          <h1 className="text-2xl font-semibold">Không tìm thấy trang</h1>
          <p className="max-w-md text-sm text-slate-600">
            Đường dẫn không tồn tại hoặc đã được di chuyển.
          </p>
          <Link
            className="rounded-lg bg-brand px-4 py-2 text-sm font-semibold text-white hover:bg-brand-hover"
            href="/"
          >
            Về trang chủ
          </Link>
        </main>
      </body>
    </html>
  );
}
