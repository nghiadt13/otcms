import { ResultDetailScreen } from "@/features/medical-records/components/ResultDetailScreen";
import { VisitReviewAction } from "@/features/patient-reviews/components/VisitReviewAction";

export const metadata = {
  title: "Chi tiết kết quả khám | OTCMS",
};

// `key` theo mã lượt: chuyển sang lượt trước/sau thì màn tải lại từ đầu, không giữ dữ liệu cũ.
export default async function PatientResultDetailPage({
  params,
}: {
  params: Promise<{ visitId: string }>;
}) {
  const { visitId } = await params;
  return (
    <ResultDetailScreen
      key={visitId}
      reviewAction={<VisitReviewAction visitId={visitId} />}
      visitId={visitId}
    />
  );
}
