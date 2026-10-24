import type { Metadata } from "next";
import "@/styles/app-theme.css";
import { appFont } from "@/lib/fonts";
import { cn } from "@/lib/utils";

// Root layout của khu Lễ tân (/staff): nạp theme shadcn và font Be Vietnam Pro như khu
// admin. Chuyển giữa khu Lễ tân và các khu khác là tải lại cả trang (xem architecture.md).
export const metadata: Metadata = {
  title: "Lễ tân | OTCMS",
  description: "Orthopedic Clinic Management System",
};

export default function StaffRootLayout({
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
