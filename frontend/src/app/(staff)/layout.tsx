import type { Metadata } from "next";
import "@/styles/app-theme.css";
import "./staff.css";
import { appFont } from "@/lib/fonts";
import { cn } from "@/lib/utils";

export const metadata: Metadata = {
  title: "Lễ tân | OTCMS",
  description: "Tiếp nhận và điều phối khám chấn thương chỉnh hình",
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
