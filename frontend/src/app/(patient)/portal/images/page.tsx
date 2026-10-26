import { Suspense } from "react";
import { PatientStudyListScreen } from "@/features/imaging/components/PatientStudyListScreen";

export const metadata = {
  title: "Ảnh chụp | OTCMS",
};

// useSearchParams trong màn cần Suspense để trang vẫn dựng tĩnh được.
export default function PatientImagesPage() {
  return (
    <Suspense fallback={null}>
      <PatientStudyListScreen />
    </Suspense>
  );
}
