import { Suspense } from "react";
import { StaffShiftsManagement } from "@/features/staff-shifts/components/StaffShiftsManagement";
export const metadata = { title: "Ca làm việc | OTCMS Admin" };
export default function Page() {
  return (
    <Suspense fallback={null}>
      <StaffShiftsManagement />
    </Suspense>
  );
}
