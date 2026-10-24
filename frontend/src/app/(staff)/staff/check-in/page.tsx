import type { Metadata } from "next";
import { Suspense } from "react";
import { PatientLookup } from "@/features/patients/components/PatientLookup";
import { CheckInWorkspace } from "@/features/reception/components/CheckInWorkspace";
import { STAFF_ROUTES } from "@/lib/routes/staffRoutes";

export const metadata: Metadata = {
  title: "Tiếp nhận bệnh nhân",
};

// Route ghép hai feature: tìm bệnh nhân (patients) và tiếp nhận (reception). Feature không import
// nhau, nên chỉ trang này import PatientLookup và đưa vào qua lookupSlot. useSearchParams trong
// feature cần Suspense để trang vẫn dựng tĩnh được.
export default function CheckInPage() {
  return (
    <Suspense fallback={null}>
      <CheckInWorkspace
        lookupSlot={<PatientLookup selectHref={STAFF_ROUTES.checkIn} autoFocus />}
      />
    </Suspense>
  );
}
