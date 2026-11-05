import { Suspense } from "react";
import { PatientImagingHistoryScreen } from "@/features/imaging/components/PatientImagingHistoryScreen";

export const metadata = {
  title: "Ảnh chụp | OTCMS",
};

// useSearchParams trong màn cần Suspense để trang vẫn dựng tĩnh được.
export default function PatientImagesPage() {
  return (
    <Suspense fallback={null}>
      <PatientImagingHistoryScreen />
    </Suspense>
  );
}
