import { Suspense } from "react";
import { ResultListScreen } from "@/features/medical-records/components/ResultListScreen";

export const metadata = {
  title: "Kết quả khám | OTCMS",
};

// useSearchParams trong màn cần Suspense để trang vẫn dựng tĩnh được.
export default function PatientResultsPage() {
  return (
    <Suspense fallback={null}>
      <ResultListScreen />
    </Suspense>
  );
}
