import type { Metadata } from "next";
import { Suspense } from "react";
import { StaffShiftsManagement } from "@/features/staff-shifts/components/StaffShiftsManagement";

export const metadata: Metadata = { title: "Ca làm việc" };

export default function AdminStaffShiftsPage() {
  // Màn đọc kỳ và bộ lọc từ URL (useSearchParams) nên cần Suspense.
  return (
    <Suspense fallback={null}>
      <StaffShiftsManagement />
    </Suspense>
  );
}
