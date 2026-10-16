import type { Metadata } from "next";
import { Geist } from "next/font/google";
import "@/styles/app-theme.css";
import { cn } from "@/lib/utils";

const geist = Geist({ subsets: ["latin"], variable: "--font-sans" });

// Root layout của khu quản trị: nạp theme shadcn (biến màu, @layer base) và font
// Geist. Các khu khác (patient, staff) cần theme này thì tự import cùng file.
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
    <html lang="vi" className={cn("font-sans", geist.variable)}>
      <body>{children}</body>
    </html>
  );
}
