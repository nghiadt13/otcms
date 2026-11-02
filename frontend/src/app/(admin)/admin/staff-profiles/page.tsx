import { Suspense } from "react";
import { StaffProfileManagement } from "@/features/staff-profiles/components/StaffProfileManagement";

export const metadata = {
  title: "Hồ sơ nhân viên | OTCMS Admin",
};

export default function AdminStaffProfilesPage() {
  // useSearchParams (đọc ?accountId=) cần Suspense khi build.
  return (
    <Suspense>
      <StaffProfileManagement />
    </Suspense>
  );
}
