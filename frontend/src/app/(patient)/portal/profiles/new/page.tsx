import { NewPatientProfileScreen } from "@/features/patient-profiles/components/NewPatientProfileScreen";

export const metadata = {
  title: "Thêm hồ sơ bệnh nhân | OTCMS",
};

// Gắn hồ sơ đã có tại phòng khám (theo email) hoặc tạo hồ sơ mới cho người chưa từng khám.
export default function NewPatientProfilePage() {
  return <NewPatientProfileScreen />;
}
