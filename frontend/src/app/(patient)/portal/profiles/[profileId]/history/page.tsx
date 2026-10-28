import { PatientProfileHistoryScreen } from "@/features/patient-profiles/components/PatientProfileHistoryScreen";

export const metadata = {
  title: "Tiền sử bệnh | OTCMS",
};

// `key` theo mã hồ sơ: đổi hồ sơ thì màn tải lại tiền sử từ đầu.
export default async function PatientProfileHistoryPage({
  params,
}: {
  params: Promise<{ profileId: string }>;
}) {
  const { profileId } = await params;
  return <PatientProfileHistoryScreen key={profileId} profileId={profileId} />;
}
