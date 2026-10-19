import type { Metadata } from "next";
import "@/styles/app-theme.css";
import { appFont } from "@/lib/fonts";
import { cn } from "@/lib/utils";

// Root layout của khu quản trị: nạp theme shadcn (biến màu, @layer base) và font
// chung Be Vietnam Pro. Các khu khác (patient, staff) cần theme này thì tự import cùng file.
export const metadata: Metadata = {
  title: "Quản trị | OTCMS",
  description: "Orthopedic Clinic Management System",
};

export default function AdminRootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="vi" className={cn("font-sans", appFont.variable)}>
      <body>{children}</body>
    </html>
  );
}
