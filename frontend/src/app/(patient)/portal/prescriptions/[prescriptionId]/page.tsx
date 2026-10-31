import { PrescriptionDetailScreen } from "@/features/prescriptions/components/PrescriptionDetailScreen";

export const metadata = {
  title: "Chi tiết đơn thuốc | OTCMS",
};

// `key` theo mã đơn: đổi đơn thì màn tải lại từ đầu.
export default async function PatientPrescriptionDetailPage({
  params,
}: {
  params: Promise<{ prescriptionId: string }>;
}) {
  const { prescriptionId } = await params;
  return (
    <PrescriptionDetailScreen
      key={prescriptionId}
      prescriptionId={prescriptionId}
    />
  );
}
