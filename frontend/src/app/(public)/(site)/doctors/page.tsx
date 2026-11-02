import type { Metadata } from "next";
import { PublicPageHeader } from "@/components/brand/PublicPageHeader";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { listPublicDoctors } from "@/features/staff-profiles/api/publicDoctorApi";
import { DoctorDirectory } from "@/features/staff-profiles/components/DoctorDirectory";

export const metadata: Metadata = { title: "Đội ngũ bác sĩ | OTCMS" };

// UC16 Xem bác sĩ: danh bạ chỉ gồm bác sĩ đã bật hiển thị hồ sơ công khai (BR-09).
export default async function DoctorsPage() {
  const { items } = await listPublicDoctors();
  return (
    <div className={`${publicContainerClass} py-16 lg:py-24`}>
      <PublicPageHeader
        description="Bác sĩ chỉnh hình đang nhận khám. Chọn một bác sĩ để xem chuyên môn và lịch nhận khám 7 ngày tới."
        title="Đội ngũ bác sĩ"
      />
      <DoctorDirectory doctors={items} />
    </div>
  );
}
