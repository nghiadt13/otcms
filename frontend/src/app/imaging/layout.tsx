import type { Metadata } from "next";
import { ImagingShell } from "@/features/imaging/components/ImagingShell";
import "./imaging-root.css";

export const metadata: Metadata = {
  title: "Kỹ thuật viên chẩn đoán hình ảnh | OTCMS",
  description:
    "Không gian làm việc của kỹ thuật viên chẩn đoán hình ảnh OTCMS.",
};
export default function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="vi">
      <body>
        <ImagingShell>{children}</ImagingShell>
      </body>
    </html>
  );
}
