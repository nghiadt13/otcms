import type { Metadata } from "next";
import { ImagingReconciliationAdminScreen } from "@/features/imaging/components/ImagingReconciliationAdminScreen";

export const metadata: Metadata = { title: "Đối soát DICOM" };

export default function ImagingReconciliationPage() {
  return <ImagingReconciliationAdminScreen />;
}
