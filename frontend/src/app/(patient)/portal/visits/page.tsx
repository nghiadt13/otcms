import { Suspense } from "react";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { VisitList } from "@/features/clinic-visits/components/VisitList";
import { VisitListSkeleton } from "@/features/clinic-visits/components/VisitListBody";

export const metadata = {
  title: "Lượt khám | OTCMS",
};

// Danh sách lượt khám. Tab và người khám đọc từ URL nên danh sách nằm trong Suspense.
export default function PatientVisitsPage() {
  return (
    <>
      <PortalPageHeader
        description="Số thứ tự, tiến độ và chỉ định của lượt khám của bạn và người thân."
        title="Lượt khám"
      />
      <Suspense fallback={<VisitListSkeleton />}>
        <VisitList />
      </Suspense>
    </>
  );
}
