import { PatientImagingStudyDetailScreen } from "@/features/imaging/components/PatientImagingStudyDetailScreen";

export const metadata = {
  title: "Chi tiết ảnh chụp | OTCMS",
};

// `key` theo mã study: đổi study thì màn tải lại từ đầu.
export default async function PatientImageStudyPage({
  params,
  searchParams,
}: {
  params: Promise<{ studyId: string }>;
  searchParams: Promise<{ profileId?: string | string[] }>;
}) {
  const { studyId } = await params;
  const { profileId } = await searchParams;
  const initialProfileId =
    typeof profileId === "string" ? profileId : undefined;
  return (
    <PatientImagingStudyDetailScreen
      key={`${studyId}:${initialProfileId ?? ""}`}
      studyRef={studyId}
      initialProfileId={initialProfileId}
    />
  );
}
