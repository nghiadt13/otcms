import type { Metadata } from "next";
import { cache } from "react";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { loadOrNotFound } from "@/lib/api/loadOrNotFound";
import { getPublicDoctor } from "@/features/staff-profiles/api/publicDoctorApi";
import { DoctorPublicProfile } from "@/features/staff-profiles/components/DoctorPublicProfile";

type Props = { params: Promise<{ doctorId: string }> };

// Hồ sơ bác sĩ công khai (UC16 bước 3–4). Bác sĩ đã tắt hiển thị hoặc đã gỡ → 404 MSG-24
// qua `doctors/[doctorId]/not-found.tsx`.
const loadDoctor = cache((doctorId: string) =>
  loadOrNotFound(() => getPublicDoctor(doctorId)),
);

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { doctorId } = await params;
  const doctor = await loadDoctor(doctorId);
  return { title: `${doctor.fullName} | Đội ngũ bác sĩ` };
}

export default async function DoctorProfilePage({ params }: Props) {
  const { doctorId } = await params;
  const doctor = await loadDoctor(doctorId);
  return (
    <div className={`${publicContainerClass} py-16 lg:py-24`}>
      <DoctorPublicProfile doctor={doctor} />
    </div>
  );
}
