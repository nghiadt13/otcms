"use client";

import { useRouter } from "next/navigation";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { CreateProfileForm } from "./CreateProfileForm";

// Trang "Thêm thành viên": chỉ có form tạo hồ sơ mới cho người thân chưa từng khám.
export function NewPatientProfileScreen() {
  const router = useRouter();

  return (
    <>
      <PortalPageHeader
        description="Tạo hồ sơ mới cho người thân chưa từng khám tại phòng khám."
        title="Thêm thành viên"
      />
      <div className="space-y-10 rounded-lg border bg-card p-5 md:p-8">
        <CreateProfileForm
          onCreated={(profileId) =>
            router.push(PATIENT_ROUTES.profileDetail(profileId))
          }
        />
      </div>
    </>
  );
}
