import type { Metadata } from "next";
import { PersonalInfoScreen } from "./_personal/PersonalInfoScreen";

export const metadata: Metadata = {
  title: "Thông tin cá nhân | Cổng bệnh nhân",
};

// Thông tin cá nhân của bệnh nhân (UC03, UC04): ảnh, họ tên, ngày sinh, giới tính, CCCD, SĐT,
// địa chỉ, email đăng nhập. Phần ghép hai feature nằm ở `_personal`. `?next=` là trang đang dở
// (ví dụ đặt lịch) mà người dùng quay lại sau khi điền xong hồ sơ lần đầu.
export default async function PatientAccountPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string | string[] }>;
}) {
  const { next } = await searchParams;
  return <PersonalInfoScreen next={Array.isArray(next) ? next[0] : next} />;
}
