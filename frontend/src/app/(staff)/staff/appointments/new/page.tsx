import type { Metadata } from "next";
import { Suspense } from "react";
import { PageHeader } from "@/components/PageHeader";
import { StaffBookingPanel } from "@/features/appointments/components/staff/StaffBookingPanel";
import { PatientLookup } from "@/features/patients/components/PatientLookup";
import { STAFF_ROUTES } from "@/lib/routes/staffRoutes";

export const metadata: Metadata = {
  title: "Đặt lịch",
};

// Route ghép hai feature: tìm bệnh nhân (patients) và đặt lịch (appointments). Feature không import
// nhau, nên chỉ trang này import cả hai: PatientLookup được truyền vào làm lookupSlot và chọn
// người đưa `?patientId=` lên URL. useSearchParams trong feature cần Suspense.
export default function StaffNewAppointmentPage() {
  return (
    <>
      <PageHeader
        title="Đặt lịch"
        description="Tìm bệnh nhân bằng số điện thoại và ngày sinh, rồi chọn giờ khám."
      />
      <Suspense fallback={null}>
        <StaffBookingPanel
          lookupSlot={
            <PatientLookup selectHref={STAFF_ROUTES.newAppointment} autoFocus />
          }
        />
      </Suspense>
    </>
  );
}
