import { PatientProfileDetailScreen } from "@/features/patient-profiles/components/PatientProfileDetailScreen";

export const metadata = {
  title: "Chi tiết hồ sơ bệnh nhân | OTCMS",
};

// `key` theo mã hồ sơ: chuyển sang hồ sơ khác thì màn tải lại từ đầu, không giữ dữ liệu cũ.
export default async function PatientProfileDetailPage({
  params,
}: {
  params: Promise<{ profileId: string }>;
}) {
  const { profileId } = await params;
  return <PatientProfileDetailScreen key={profileId} profileId={profileId} />;
}
