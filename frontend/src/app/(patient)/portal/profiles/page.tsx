import { PatientProfilesScreen } from "@/features/patient-profiles/components/PatientProfilesScreen";

export const metadata = {
  title: "Hồ sơ bệnh nhân | OTCMS",
};

// Hồ sơ đã gắn của tài khoản, kèm băng gợi ý hồ sơ khám tại phòng khám dùng cùng email.
export default function PatientProfilesPage() {
  return <PatientProfilesScreen />;
}
