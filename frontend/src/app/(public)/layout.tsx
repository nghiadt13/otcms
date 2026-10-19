import type { Metadata } from "next";
import "@/styles/app-theme.css";
import { appFont } from "@/lib/fonts";

// Root layout của khu công khai (trang chủ, đăng nhập, đăng ký, workspace). Nạp
// cùng theme shadcn với khu admin (`styles/app-theme.css`) để mọi khu dùng chung
// component `components/ui` và token màu. Chuyển giữa hai root layout `(public)`
// và `(admin)` vẫn là tải lại cả trang.
export const metadata: Metadata = {
  title: "OTCMS | Quản lý phòng khám chỉnh hình",
  description: "Orthopedic Clinic Management System",
};

export default function PublicRootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="vi" className={appFont.variable}>
      <body>{children}</body>
    </html>
  );
}
