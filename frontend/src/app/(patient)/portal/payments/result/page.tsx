import { notFound } from "next/navigation";
import { PaymentResultView } from "@/features/billing/components/portal/PaymentResultView";
import { readStringParam } from "@/features/billing/components/portal/patientBillingPresentation";

// Kết quả thanh toán sau khi VNPay chuyển về: `?paymentId=`. Thiếu mã là 404.
export default async function PaymentResultPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  const paymentId = readStringParam(params.paymentId);
  if (!paymentId) notFound();
  return <PaymentResultView paymentId={paymentId} />;
}
