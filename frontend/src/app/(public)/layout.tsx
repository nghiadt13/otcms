import type { Metadata } from "next";
import "./public.css";

// Root layout của khu công khai (trang chủ, đăng nhập, workspace). Chỉ nạp
// Tailwind thuần; theme shadcn nằm ở `styles/app-theme.css` và chỉ khu `(admin)`
// nạp, nên đổi theme admin không làm đổi giao diện các trang này.
// Chuyển giữa hai root layout `(public)` và `(admin)` là tải lại cả trang.
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
    <html lang="vi">
      <body>{children}</body>
    </html>
  );
}
