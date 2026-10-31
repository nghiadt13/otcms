import { notFound } from "next/navigation";
import { PayOsAttemptResultView } from "@/features/billing/components/portal/PayOsAttemptResultView";
import { PaymentResultView } from "@/features/billing/components/portal/PaymentResultView";
import { readStringParam } from "@/features/billing/components/portal/patientBillingPresentation";

// Kết quả PayOS đọc từ backend bằng attemptId. Giữ paymentId cho các kịch bản mock cũ.
export default async function PaymentResultPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  const attemptId = readStringParam(params.attemptId);
  if (attemptId)
    return (
      <PayOsAttemptResultView
        attemptId={attemptId}
        cancelled={readStringParam(params.cancelled) === "true"}
      />
    );
  const paymentId = readStringParam(params.paymentId);
  if (!paymentId) notFound();
  return <PaymentResultView paymentId={paymentId} />;
}
