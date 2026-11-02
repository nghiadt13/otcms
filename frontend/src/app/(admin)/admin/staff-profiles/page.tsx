import { Suspense } from "react";
import { UnifiedStaffProfiles } from "@/features/staff-profiles/components/UnifiedStaffProfiles";
export const metadata = { title: "Hồ sơ nhân viên | OTCMS Admin" };
export default function Page() {
  return (
    <Suspense fallback={null}>
      <UnifiedStaffProfiles />
    </Suspense>
  );
}
