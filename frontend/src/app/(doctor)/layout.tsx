import type { Metadata } from "next";
import "@/styles/app-theme.css";
import { appFont } from "@/lib/fonts";
import { cn } from "@/lib/utils";

// Root layout của khu bác sĩ: nạp theme shadcn và font chung Be Vietnam Pro, giống khu quản trị.
export const metadata: Metadata = {
  title: "Bác sĩ | OTCMS",
  description: "Không gian làm việc của bác sĩ chỉnh hình OTCMS",
};

export default function DoctorRootLayout({
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
