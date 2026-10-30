import { Suspense } from "react";
import { PrescriptionListScreen } from "@/features/prescriptions/components/PrescriptionListScreen";

export const metadata = {
  title: "Đơn thuốc | OTCMS",
};

// useSearchParams trong màn cần Suspense để trang vẫn dựng tĩnh được.
export default function PatientPrescriptionsPage() {
  return (
    <Suspense fallback={null}>
      <PrescriptionListScreen />
    </Suspense>
  );
}
