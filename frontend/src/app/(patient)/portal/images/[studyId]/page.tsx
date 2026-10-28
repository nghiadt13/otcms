import { PatientStudyDetailScreen } from "@/features/imaging/components/PatientStudyDetailScreen";

export const metadata = {
  title: "Chi tiết ảnh chụp | OTCMS",
};

// `key` theo mã study: đổi study thì màn tải lại từ đầu.
export default async function PatientImageStudyPage({
  params,
}: {
  params: Promise<{ studyId: string }>;
}) {
  const { studyId } = await params;
  return <PatientStudyDetailScreen key={studyId} studyId={studyId} />;
}
