import { notFound } from "next/navigation";
import { PayableSourceDetail } from "@/features/billing/components/portal/PayableSourceDetail";
import { isPaymentSourceKind } from "@/features/billing/components/portal/patientBillingPresentation";

// Chi tiết một lịch hẹn hoặc lượt khám để chọn khoản cần trả. Loại không hợp lệ là 404.
export default async function PaymentSourcePage({
  params,
}: {
  params: Promise<{ sourceKind: string; sourceId: string }>;
}) {
  const { sourceKind, sourceId } = await params;
  if (!isPaymentSourceKind(sourceKind)) notFound();
  return <PayableSourceDetail id={sourceId} kind={sourceKind} />;
}
