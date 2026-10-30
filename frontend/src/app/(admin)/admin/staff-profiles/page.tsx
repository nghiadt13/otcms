import { AdminIntegrationMode } from "@/components/admin/AdminIntegrationMode";
import { StaffProfileManagement } from "@/features/staff-profiles/components/StaffProfileManagement";
import { Suspense } from "react";
import { AdminDoctorsLive } from "@/features/staff-profiles/components/AdminDoctorsLive";

export const metadata = {
  title: "Hồ sơ bác sĩ | OTCMS Admin",
};

export default function AdminStaffProfilesPage() {
  // useSearchParams (đọc ?accountId=) cần Suspense khi build.
  return (
    <Suspense>
      <AdminIntegrationMode
        live={<AdminDoctorsLive />}
        prototype={<StaffProfileManagement />}
      />
    </Suspense>
  );
}
