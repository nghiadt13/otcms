import type { Metadata } from "next";
import { MyAccountProfile } from "@/features/my-account/components/MyAccountProfile";

import { DoctorIntroductionForm } from "@/features/staff-profiles/components/DoctorIntroductionForm";

export const metadata: Metadata = { title: "Hồ sơ tài khoản" };

export default function DoctorAccountPage() {
  return (
    <>
      <MyAccountProfile />
      <DoctorIntroductionForm />
    </>
  );
}
