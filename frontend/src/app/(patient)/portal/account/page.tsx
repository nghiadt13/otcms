import type { Metadata } from "next";
import { PersonalInfoScreen } from "./_personal/PersonalInfoScreen";

export const metadata: Metadata = {
  title: "Thông tin cá nhân | Cổng bệnh nhân",
};

// Thông tin cá nhân của bệnh nhân (UC03, UC04): ảnh, họ tên, ngày sinh, giới tính, CCCD, SĐT,
// địa chỉ, email đăng nhập. Phần ghép hai feature nằm ở `_personal`.
export default function PatientAccountPage() {
  return <PersonalInfoScreen />;
}
