import { AdminIntegrationMode } from "@/components/admin/AdminIntegrationMode";
import { StaffShiftsManagement } from "@/features/staff-shifts/components/StaffShiftsManagement";
import type { Metadata } from "next";
import { Suspense } from "react";
import { AdminDoctorScheduleLive } from "@/features/staff-shifts/components/AdminDoctorScheduleLive";

export const metadata: Metadata = { title: "Ca trực và ngày nghỉ bác sĩ" };

export default function AdminStaffShiftsPage() {
  // Màn đọc kỳ và bộ lọc từ URL (useSearchParams) nên cần Suspense.
  return (
    <Suspense fallback={null}>
      <AdminIntegrationMode
        live={<AdminDoctorScheduleLive />}
        prototype={<StaffShiftsManagement />}
      />
    </Suspense>
  );
}
