import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "OTCMS | Quản lý phòng khám chỉnh hình",
  description: "Orthopedic Clinic Management System",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="vi">
      <body>{children}</body>
    </html>
  );
}
