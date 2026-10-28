import { Suspense } from "react";
import { ReceptionScreen } from "@/features/patient-reception/components/ReceptionScreen";
import { DataLoading } from "@/features/patient-reception/components/ReceptionUi";
export default function Page() {
  return (
    <Suspense fallback={<DataLoading />}>
      <ReceptionScreen section="" />
    </Suspense>
  );
}
