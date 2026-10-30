import type { Metadata } from "next";
import "@/styles/app-theme.css";
import { appFont } from "@/lib/fonts";
import { cn } from "@/lib/utils";
export const metadata: Metadata = {
  title: "Chủ phòng khám | OTCMS",
  robots: { index: false, follow: false },
};
export default function OwnerRootLayout({
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
