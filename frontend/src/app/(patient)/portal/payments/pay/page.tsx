import { notFound } from "next/navigation";
import { PaymentConfirm } from "@/features/billing/components/portal/PaymentConfirm";
import {
  isPaymentSourceKind,
  parseItemIds,
  readStringParam,
} from "@/features/billing/components/portal/patientBillingPresentation";

// Xác nhận trước VNPay: `?source=visit|appointment&id=…&items=a,b`. Thiếu nguồn hoặc id là 404.
export default async function PaymentConfirmPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  const kind = readStringParam(params.source);
  const id = readStringParam(params.id);
  if (!isPaymentSourceKind(kind) || !id) notFound();
  return (
    <PaymentConfirm
      id={id}
      itemIds={parseItemIds(readStringParam(params.items))}
      kind={kind}
    />
  );
}
