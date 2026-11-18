import { notFound } from "next/navigation";
import { PayOsAttemptResultView } from "@/features/billing/components/portal/PayOsAttemptResultView";
import { readStringParam } from "@/features/billing/components/portal/patientBillingPresentation";

// Kết quả PayOS đọc từ backend bằng attemptId. Thiếu attemptId là 404.
export default async function PaymentResultPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  const attemptId = readStringParam(params.attemptId);
  if (!attemptId) notFound();
  return (
    <PayOsAttemptResultView
      attemptId={attemptId}
      cancelled={readStringParam(params.cancelled) === "true"}
    />
  );
}
