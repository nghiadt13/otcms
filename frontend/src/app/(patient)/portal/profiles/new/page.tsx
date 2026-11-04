import { NewPatientProfileScreen } from "@/features/patient-profiles/components/NewPatientProfileScreen";

export const metadata = {
  title: "Thêm thành viên | OTCMS",
};

// Tạo hồ sơ mới cho người thân chưa từng khám.
export default function NewPatientProfilePage() {
  return <NewPatientProfileScreen />;
}
