import { VisitDetail } from "@/features/clinic-visits/components/VisitDetail";

export const metadata = {
  title: "Chi tiết lượt khám | OTCMS",
};

// `key` theo mã lượt: chuyển sang lượt khác thì màn tải lại từ đầu, không giữ dữ liệu cũ.
export default async function PatientVisitDetailPage({
  params,
}: {
  params: Promise<{ visitId: string }>;
}) {
  const { visitId } = await params;
  return <VisitDetail key={visitId} id={visitId} />;
}
