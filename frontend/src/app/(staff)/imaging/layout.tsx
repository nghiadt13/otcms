import type { Metadata } from "next";
import { ImagingShell } from "@/features/imaging/components/ImagingShell";

export const metadata: Metadata = {
  title: "Kỹ thuật viên chẩn đoán hình ảnh | OTCMS",
  description:
    "Không gian làm việc của kỹ thuật viên chẩn đoán hình ảnh OTCMS.",
};
export default function Layout({ children }: { children: React.ReactNode }) {
  return <ImagingShell>{children}</ImagingShell>;
}
