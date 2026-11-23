import type { Metadata } from "next";
import "@/styles/app-theme.css";
import { appFont } from "@/lib/fonts";
import { brandIcons } from "@/lib/brandIcons";
import { cn } from "@/lib/utils";
export const metadata: Metadata = {
  title: "Chủ phòng khám | OTCMS",
  icons: brandIcons,
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
