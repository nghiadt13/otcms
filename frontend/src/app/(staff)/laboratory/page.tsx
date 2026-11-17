import type { Metadata } from "next";
import { LaboratoryPreview } from "@/features/laboratory/components/LaboratoryPreview";

export const metadata: Metadata = { title: "Xét nghiệm | OTCMS" };

export default function LaboratoryPage() {
  return <LaboratoryPreview />;
}
