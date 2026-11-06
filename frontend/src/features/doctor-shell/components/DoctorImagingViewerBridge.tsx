"use client";
import type { ReactNode } from "react";
import { DoctorImagingViewerProvider } from "@/features/doctor-exam/components/DoctorImagingViewer";
import { ImagingViewerLauncher } from "@/features/imaging/components/ImagingViewerLauncher";
function renderDoctorViewer(studyRef: string) {
  return (
    <ImagingViewerLauncher
      key={studyRef}
      studyRef={studyRef}
      purpose="DOCTOR_REVIEW"
    />
  );
}
/** Compose Long's secure launcher without cross-feature business imports. */
export function DoctorImagingViewerBridge({
  children,
}: {
  children: ReactNode;
}) {
  return (
    <DoctorImagingViewerProvider renderViewer={renderDoctorViewer}>
      {children}
    </DoctorImagingViewerProvider>
  );
}
